# Le regole, con il perché

Ognuna di queste risolve un modo concreto in cui l'interfaccia si rompe. Conoscere il perché serve a
sapere quando una regola non si applica, invece di aggirarla.

## Angoli continui, mai circolari

`ContinuousCornerShape`, non `RoundedCornerShape`.

Un angolo circolare unisce un lato dritto a un arco: la curvatura passa da zero a `1/r` in un punto
solo, e l'occhio legge quella discontinuità come una piega. Un angolo continuo fa salire e scendere
la curvatura lungo il lato, e non lascia giunzione. È metà del motivo per cui una card qui non
sembra una card Material — l'altra metà è la tipografia.

Conseguenza pratica: un angolo continuo si estende più a lungo lungo il lato, quindi a parità di
numero sembra più stretto. I raggi di `FluidRadius` sono già più grandi degli equivalenti Material
apposta. Non "correggerli" verso i valori Material.

```kotlin
Modifier.clip(ContinuousCornerShape(FluidRadius.Card))   // sì
Modifier.clip(RoundedCornerShape(16.dp))                 // no
```

`MaterialTheme.shapes` è già mappato sui raggi continui: usarlo va sempre bene.

## Tipografia: due tagli e una curva di tracking

`fluidTypography()` mappa la scala iOS (34/28/22/20/17/15/13/11) sugli slot di Material. Chi scrive
una schermata chiede `MaterialTheme.typography.titleMedium` e ottiene 17sp semibold, che è la riga
principale di ogni riga di lista.

Due cose la fanno leggere come San Francisco e non come "un grottesco carino":

- **Optical sizing**: sopra i 20sp si usa il taglio Display (spaziatura più stretta), sotto il taglio
  Text. È automatico dentro `fluidStyle`.
- **Tracking dipendente dalla dimensione**: negativo nei titoli, neutro a 13sp, positivo sotto. Inter
  a tracking zero sembra largo nei titoli e stretto nelle didascalie.

Non scrivere `fontSize` a mano. Se manca uno stile, aggiungilo a `FluidTextStyles` con la stessa
curva, non nella schermata.

Per numeri in colonna (voti, date, conteggi) usa `FluidTextStyles.numeric` o `largeNumeric`: hanno
le cifre tabulari, altrimenti la colonna balla mentre i numeri cambiano.

## Una palette da un colore solo

`FluidTheme(settings, brand)` genera l'intera scala di superfici dall'accento. Le superfici grandi
restano quasi neutre, i contenitori piccoli e alti prendono progressivamente più tinta.

I ruoli sono **coppie**: `primaryContainer`/`onPrimaryContainer`, mai un colore di sfondo separato da
quello di contenuto. Se ti serve un colore nuovo, cercalo prima fra i ruoli esistenti; se davvero non
c'è (warning e success non hanno un ruolo Material), aggiungi una coppia fissa con i due valori
chiaro/scuro, come già fanno `FluidTone.Warning` e `FluidTone.Success`.

Il contrasto è verificato da `FluidContrastTest`. Se aggiungi un ruolo, aggiungi il caso.

## Liste raggruppate

Righe dentro un unico contenitore arrotondato, separate da hairline da 0.5dp rientrate dove comincia
il testo.

Il tono della riga sta sulla **piastrella dell'icona**, mai sullo sfondo della riga. Colorare le
righe trasforma un gruppo ordinato in un patchwork di blocchi colorati: era così prima, ed è la cosa
che rendeva l'app "colorata" invece che leggibile.

Il feedback al tocco per una riga è `fluidRowPressable` (tinta), non `fluidPressable` (scala): una
riga che si rimpicciolisce rompe la sagoma del gruppo e fa sembrare la lista instabile.

## I pulsanti crescono, le superfici cedono (2.11.0, come LibrePods dalla 3.0)

Un pulsante è una cosa piccola e sola: sotto il dito **cresce** (`FluidPressFeedback.Expand`), lo
segue e vibra mentre il dito ci scorre sopra. Una card, una tessera, una cella **cedono** di pochi
centesimi (`yielding`): crescendo invaderebbero le vicine, e seguendo il dito farebbero ballare la
griglia. Non scrivere una terza risposta al dito in una schermata: è `FluidPress.kt`, o non è.

**Dalla 3.0 il pulsante è quello di LibrePods, e lo è davvero.** È una scelta di Alessio: il
riferimento è un'app che, in mano, risponde meglio della nostra, e le differenze erano poche e tutte
nello stesso verso — un tasto che aspettava, che si prendeva il dito, che vibrava una volta sola.
L'engine ora porta il codice dei suoi pulsanti (GPL-3.0: vedi `LICENSES/LibrePods.md` e perché una
app che lo distribuisce è GPL-3.0). Cosa vale, e cosa è cambiato dalla 2.12:

- **la pressione parte al contatto.** Senza ritardo, senza soglia, in una lista come in una barra.
  Prima, dentro una pagina che scorre, partiva 100 ms dopo (e il tasto "arrivava" invece di
  "scattare"); `LocalFluidPressImmediate` non serve più a niente e non fa più niente;
- **il pulsante non prende mai il gesto.** Non consuma niente: se il dito che è partito su un pulsante
  scorre, la pagina scorre *e* il pulsante cresce, segue il dito e vibra, insieme, fino al
  sollevamento. **Sostituisce la regola della 2.12** ("un pulsante è di chi lo tiene premuto":
  100 ms di attesa, poi il gesto era suo e la pagina sotto non partiva), che aveva un prezzo — una
  lista fatta di pulsanti si scorreva solo dai margini — e che la mano ha giudicato peggiore;
- **il click è di `clickable`, come in LibrePods.** Scatta se il dito si solleva sul pulsante e
  nessuno ha consumato: dopo uno scorrimento non c'è click. Tastiera, fuoco, TalkBack e ruolo sono
  suoi, come prima. Il **click lungo resta** (ai menu serve): scatta se il dito è rimasto entro lo
  slop per il tempo del click lungo, e dopo non c'è click al rilascio;
- **i numeri sono i suoi.** Molla di pressione e di discesa (0,5; 300), deriva del dito
  `m · tanh(0,05 · d / m)` col ritorno a molla critica (1; 300), allungamento direzionale lineare e
  senza tetto, crescita `1 + maxScale`: 0,10 per un tasto con testo (`Expand`), **0,5 per un tasto
  tondo** (`ExpandRound`: un tasto di vetro da 44 dp arriva a 66, al picco della molla a 70). Un
  tetto sul lato lungo (16 dp) fa il lavoro del `maxScale = 0,05` che LibrePods dà a mano ai suoi
  tasti a tutta larghezza. Il rilascio parte subito dal valore e dalla velocità che trova: **non
  aspetta più** di arrivare in cima (restava la regola per le superfici e per le animazioni ridotte);
- **l'aptica è una coreografia, con le costanti di sistema.** `PressDown` al contatto
  (`SegmentFrequentTick`), un `SegmentFrequentTick` per ogni evento che si sposta di più di
  sqrt(350) px (stile di grana `LibrePods`, di serie), `PressRelease` al rilascio (`Reject`), `PressClick`
  al click (`ContextClick`). Un tap fa quindi tre colpi: **è l'unica eccezione a "una vibrazione per
  momento"**, ed è voluta (la regola e il perché sono più sotto, in «Una vibrazione per momento»). Un tasto
  che apre qualcosa di suo (un menu) passa il proprio evento a
  `fluidPressable(haptic = ...)` al posto di `PressClick`; `haptic = null` spegne tutto;
- **sul vetro**, la lente sotto il dito è quella di LibrePods (12/24 dp, profondità, dispersione:
  `GlassDefaults.buttonOptics()`, a riposo resta quella di sempre) e il bagliore è il suo (raggio il lato
  lungo, velo 0,10, punto 0,15, `glassButtonGlowRecipe`). Su vetro chiaro il bianco additivo può
  tornare a sbiancare la faccia, come prima del 2.12.0: va guardato in mano.

Tre cose da sapere, tutte pagate:

- **`fluidPressable` sta prima di `clip` e `background`.** Dopo, la scala si applica solo a ciò che
  sta dentro: `FluidButton` ha rimpicciolito per anni la sua etichetta dentro un contenitore fermo,
  e nessuno lo vedeva perché era il 2.6% di una parola;
- **mai scalare un nodo di vetro da un antenato.** Un layer scalato sopra un vetro rifrange il
  contenuto che non c'è più: la trasformazione del tasto di vetro sta nel `layerBlock` di
  `drawBackdrop` (`glassControlSurface`), non in un `graphicsLayer` sopra;
- **la deriva e l'allungamento sono la somma dei movimenti del dito sullo schermo, non la sua
  posizione sul tasto.** Se la pagina scorre sotto un dito che sta fermo sul tasto, il tasto lo segue
  lo stesso: il dito si è mosso, anche se non sul tasto. È voluto, e per questo il click lungo misura
  lo slop sulla strada del dito e non sulla sua posizione.

## Il vetro va sul contenitore, mai sulla riga

Un `FluidListGroup` di dodici righe e' **un** nodo di vetro, non dodici. Ogni superficie costa una
registrazione di layer e una catena di `RenderEffect` sui propri limiti, quindi mettere il materiale
un livello troppo in basso porta una schermata da otto superfici a ottanta e fa cadere fotogrammi per
qualcosa che nessuno puo' vedere. Testo, icone e badge appoggiati sopra il vetro restano opachi al
100%: il materiale e' il contenitore, mai il contenuto.

Il vetro nel contenuto e' arrivato con la 1.5.0 e vuole due cose, non una:

1. **un canvas ambientale** (`FluidScreen(ambient = ...)`), perche' il vetro sopra il grigio e'
   invisibile per costruzione — una superficie che rifrange una pagina piatta produce una pagina
   piatta, e la sua assenza si legge come un bug;
2. **due registrazioni, non una.** Il canvas si disegna e si registra *prima* della lista, quindi non
   la contiene: una card dentro la lista rifrange solo la lavata sotto di se'. La chrome continua a
   rifrangere il corpo. Se una card campionasse la registrazione che la contiene, il risultato e' un
   feedback ottico — la cosa che nel vetro si vede subito e non si puo' nascondere.

`glass = true` e' una **richiesta**, non un obbligo: senza canvas in scope, o sotto API 31, il
componente disegna esattamente la superficie opaca di sempre.

## Un pannello di vetro e' una texture, e una texture ha un tetto

Ogni superficie di vetro registra un `GraphicsLayer` grande quanto se stessa, e un layer e' una
texture della GPU: qualche migliaio di pixel per lato, dipende dal dispositivo. Oltre quel limite la
registrazione non fallisce rumorosamente, torna **vuota** — e una registrazione vuota sotto una tinta
traslucida e' un rettangolo nero al posto del contenuto.

La superficie che ci arriva e' sempre la stessa: **un gruppo lista**, perche' un gruppo e' alto
quanto il numero di righe che l'utente si trova ad avere. Ottantaquattro voti in un pannello sono
sedicimila pixel, ed e' successo davvero.

Dalla 1.5.1 l'engine se ne accorge da solo e registra piu' in piccolo invece di annerire
(`fitToTexture`): quello che c'e' dietro un pannello di contenuto e' una lavata ambientale, morbida
per costruzione, che a un quarto della risoluzione non perde niente. E la cattura era solo meta' del
problema: il **contenuto** del pannello passava da un secondo layer offscreen grande quanto il
pannello stesso — un'altra texture con lo stesso tetto, e per giunta ri-rasterizzata a ogni
fotogramma di scorrimento, testo compreso. Dalla 1.5.3 quel layer non esiste piu' (la pila del vetro
e' tutta `SrcOver`, e comporre isolati o in place e' identico): sul tablet e' la differenza fra una
pagina Voti a 85 ms per frame e una a 27. Niente di tutto questo e' un permesso per fare gruppi
infiniti: e' la garanzia che una lista lunga resti guardabile.

## Il vetro si tara con la lente, non con la sfocatura

Il raggio di riferimento e' **2,5 dp**. E' passato da 8 a 16, poi a 2, poi a 2,5 (il mezzo dp
dell'ultimo passo e' una taratura a occhio: il vetro di base un filo piu' smerigliato), e il giro di
mezzo e' l'errore che vale la pena non rifare: sopra circa 8 dp un pannello smette di trasmettere
un'immagine e trasmette la sua *media*, e un pannello che tiene una media e' un riempimento. Tutto
il lavoro che la lente fa sul bordo sta allora piegando un colore piatto in un altro colore piatto.

Quello che identifica il materiale e' la **dislocazione** al perimetro e la riga speculare sopra, non
la brina. Quindi il raggio resta minuscolo e la lente sale (19 e 29 dp sulla capsula flottante, i
numeri della capsula di Kyant). Le superfici che devono davvero nascondere qualcosa — la barra
superiore, sotto cui scorre testo nitido — chiedono un multiplo con `GlassOptics.blurScale`, invece
di farlo pagare a tutte.

**La taglia cambia il conto (2.12.0).** Un pannello grande smerigliato come uno piccolo sembra un
pannello piccolo ingrandito: il bordo piega l'immagine di pochi dp su una superficie larga mezzo
schermo, e lo spessore non si legge piu'. Quindi le superfici *piccole* (lato corto fino a 64 dp: tasti,
chip, pomelli, la pillola di navigazione) restano com'erano — guadagno esattamente zero, non "quasi
zero" — e sopra il guadagno sale a smoothstep fino ai 320 dp, dove la sfocatura e' **x2**, l'altezza
di rifrazione **x1,6** (mai oltre mezzo lato corto, dove lo shader satura) e lo spostamento al bordo
**x1,8**. La formula e' una sola, `glassSizedOptics`, per `glassSurface` e `fluidPhysicsSurface`; nel
morph conta la taglia della *sagoma*, non quella del layer che la ospita. Due vincoli che non si
toccano: **la sfocatura non cresce oltre l'altezza di lente che la copre**, perche' il padding della
cattura vale `max(0, sfocatura - altezza)` e deve restare zero anche mentre una sagoma cambia taglia
(un padding che si muove sporca la cattura a ogni fotogramma del morph); e **sotto API 33, senza
lente, la regola e' spenta**. Si esce con `GlassOptics.sizeResponse = 0`: lo scrim dei modali lo fa,
perche' copre lo schermo senza essere un pannello grande, e sfocato fino alla media farebbe il
contrario del suo mestiere. Il tetto `blurScale` 16 vale anche per il prodotto finale.

Questo **tende la regola di sopra**, e va detto: sopra circa 8 dp un pannello trasmette la media e
non l'immagine, e un `Modal` grande a taglia piena arriva a 17,5 dp (`Floating` 9, `Content` 4).
E' voluto per i pannelli grandi (il catalogo di Kyant tiene una finestra da 250-300 dp a 16 e 8 dp),
ma oltre il doppio sarebbe l'errore del 16 dp un'altra volta: i tre massimi (x2, x1,6, x1,8) sono
numeri di partenza **da giudicare su un dispositivo**, non verita', e stanno in `GlassMaterial.kt`
con il motivo accanto perche' e' li' che si ritoccano.

**E il film del vetro non e' `MaterialTheme.surface`.** Una barra dello stesso colore del fondo non e'
un materiale traslucido, e' niente: su un tema AMOLED spariva del tutto. Il Liquid Glass e' un
materiale *chiaro e riflettente*, che anche in tema scuro si legge come un pannello piu' chiaro del
fondo. `GlassDefaults.glassFilm()` parte da due grigi fissi e prende solo un terzo della palette,
quel tanto che basta perche' segua l'accento senza farsi trascinare a fondo.

## Vetro su vetro: si sfoca la sfocatura (2.13.0)

Un tasto su un pop-up, una card su un foglio, la chiusura di una notifica, il pomello di un
interruttore su una card di vetro: quasi ogni app ha almeno un vetro appoggiato su un altro vetro. Il
vetro di sopra campiona il **materiale** di quello di sotto — il fondale come lo vede lui, sfocato,
rifratto e tinto — e ci aggiunge la propria ottica. Non e' mai piu' nitido di quello su cui sta.

Fino alla 2.12 non era cosi', e il motivo e' il piu' banale: un'app passa ai suoi controlli l'unico
stato di vetro che ha in mano, quello della pagina (`backdrop = backdrop`), e un tasto dentro un
pop-up campionava la pagina cruda con la sua sfocatura da tasto (3,5 dp) dentro un pannello che ne ha
quattro o cinque volte tanto. Il pannello si vedeva smerigliato e il tasto ci apriva dentro una
finestra nitida sulla pagina, piu' chiara dello scrim intorno. Misurato sulla galleria: dentro il tasto
le righe del fondo passavano con un'ampiezza piu' di tre volte quella del pannello.

Come funziona, perche' non va rifatto a mano:

- **lo trova l'albero, non la composizione.** Ogni nodo di vetro (`drawBackdrop`, quindi
  `glassSurface`, `glassControlSurface`, `fluidPhysicsSurface`, i pomelli) cerca il nodo di vetro
  piu' vicino fra gli antenati e ne campiona il materiale. Un modificatore non puo' dare un
  `CompositionLocal` al contenuto, un nodo puo' essere trovato da un nodo figlio: per questo vale
  anche per il pannello che un'app si fa con `glassSurface` su un suo `Box`, senza toccare una riga;
- **il materiale va nella pila al posto giusto**: sopra tutto cio' che lo stato passato contiene
  e che sta *fuori* dal pannello (la pagina dietro il pop-up), sotto la prima cosa registrata
  *dentro* (la pista di un interruttore). Il fondale passato resta quello che si vede oltre i bordi
  del pannello;
- **un cablaggio a mano vince.** Se lo stato passato contiene gia' il materiale di quel pannello
  (`exports` + `rememberCombinedGlassBackdrop`: le azioni della barra, la chiusura della notifica,
  l'indicatore della tab bar) non cambia niente;
- **il materiale esclude il contenuto.** Dentro c'e' il vetro che lo campiona, e un tasto che
  rifrange se stesso e' il feedback ottico che nel vetro non si nasconde. Testo e fratelli restano
  fuori: un tasto non sta sopra il testo del pannello, e se ci stesse una copia nitida di quel testo
  piegata nella lente sarebbe rumore;
- **costa zero finche' dentro non c'e' vetro.** Un pannello registra il proprio materiale solo
  mentre qualcuno lo campiona (il layer lo crea al primo lettore), e lo registra di nuovo solo se e'
  cambiato (geometria, o uno stato che i suoi disegni hanno letto): la cattura dentro e' un
  riferimento, e un materiale registrato a ogni fotogramma di uno scorrimento faceva rifare
  sfocatura e lente a ogni tasto per un'immagine identica. A schermo fermo
  `FluidGlassDiagnostics.totalExports`, come `totalCaptures`, non si muove;
- **le ottiche si sommano da sole.** Due sfocature gaussiane fanno una sfocatura di raggio
  √(r₁² + r₂²): un tasto da 3,5 dp su un pannello da 14 finisce a 14,4, quindi non diventa una
  macchia — non aggiunge quasi niente al frost, e la sua identita' la portano bordo, lente, tinta e
  ombra, che restano. Non serve una regola che tolga sfocatura al figlio: misurato, resta un oggetto.

Le eccezioni sono due, e hanno un nome. Chi ricostruisce da se' il materiale del pannello su cui
sta (il puck della barra flottante e la riga nascosta che guarda: sfocano come la barra partendo
dalla pagina cruda) dichiara `nestInContainer = false`, altrimenti sfocherebbe due volte (il
difetto della 2.12.1). E il contenuto che **non e' figlio** del vetro su cui sta — la finestra di
Fluid-physics, il menu' che si trasforma, il pannello che un'app disegna con un altro motore — lo
dice: la superficie esporta (`exports = stato`, o `glassMaterialSource(stato)` sul nodo che porta
solo il materiale dell'app) e il contenuto si marca con `glassContainer(stato)`.

Le tre trappole di sotto valgono anche qui: il materiale e' registrato alla forza corrente del
pannello, non a quella del layer che lo fa sfumare.

## Un materiale per i pannelli (3.0)

Popover, menu contestuale, finestra che si espande, foglio, notifica, callout, bolla dell'indice di
sezione, menu che si trasforma: **un pannello flottante è un pannello flottante**, e chiede
`GlassDefaults.panelOptics(compact, overSharpPage)` e `GlassDefaults.panelTint(colour)`, mai numeri
suoi. Prima ognuno ne aveva: la finestra sfocava 2,2 con un bordo da 1,1 dp, il popover 2,5 con uno da
0,8, la notifica e il callout la lente del ruolo `Modal`. Messi uno accanto all'altro sullo schermo
erano cinque vetri per la stessa famiglia di cose, e nessuno dei cinque era sbagliato da solo: era la
somma che si leggeva.

La ricetta è quella che il popover aveva dopo i suoi giri di taratura, e i commenti accanto ai numeri
in `GlassMaterial.kt` ne sono la storia: sfocatura 2,5, lente 14/18 dp, bordo 0,8 dp al 45%, nessuna
ombra interna, ombra 18 dp al 36%. Tre cose da sapere:

- **la lente è una misura assoluta, il pannello no.** 18 dp di spostamento su una finestra larga sono
  un bordo; sugli stessi 18 dp di un menu di due righe sono un terzo del pannello, e l'immagine
  ripiegata sul margine si stacca da quella dritta al centro: si vedono **due versioni** dello stesso
  oggetto nello stesso vetro. Non è un difetto della rifrazione, è la rifrazione a una taglia per cui
  non era tarata. Per questo `compact` (lente 7/9 dp) per un menu di poche righe;
- **sopra una pagina nitida, solo la sfocatura sale** (`overSharpPage`: da 2,5 a 3,5, o a 2,8 se
  anche compatto). Un popover ha lo scrim che sfoca la pagina prima di lui; una notifica e un callout
  no, e il testo che passa dietro nitido il pannello lo deve nascondere da solo. Ma non oltre quello
  che la lente copre, perché il padding della cattura vale `max(0, sfocatura − altezza)` e deve restare
  zero in un viaggio di Fluid-physics;
- **la regola della taglia vale sopra**, come per ogni lastra: lo stesso pannello grande o piccolo esce
  dalla stessa ricetta con la sfocatura e la lente che la sua taglia chiede.

Vale per il pannello nuovo che qualcuno scrive domani: se non è nessuno di questi, è il caso che
questa regola non aveva previsto e si discute; se lo è, non si ritocca un numero nel suo file. Provato
da `GlassPanelMaterialTest` (la base è quella del popover, `compact` cambia solo la lente,
`overSharpPage` solo la sfocatura, una istanza stabile per combinazione).

## Il bordo è uno (3.0)

Il bordo speculare di una lastra lo disegnavano due strade, il renderer di `glassSurface` e il tratto
di path di `fluidPhysicsSurface`, e finché ognuna aveva la sua ricetta **la stessa lastra, ferma o in
viaggio, aveva due bordi**: il fisico era un gradiente bianco in entrambi i temi, e sul chiaro bianco
su bianco non si vede. Oggi la ricetta è una, `glassRimRecipe(darkSide)`:

- sul **lato scuro** il bordo è luce: bianco al 50% in `Plus`, perché la luce si somma;
- sul **lato chiaro** non c'è luce da sommare: è una riga **scura** (nero al 28%) posata in modo
  ordinario, `SrcOver`. Più quieta del gemello bianco, perché una riga scura su carta si legge a un
  contrasto più basso di una chiara al buio;
- il profilo è quello dello shader: pieno dove la sagoma guarda in su o in giù, zero sui fianchi.
  `fluidPhysicsSurface` non può chiedere la normale a ogni pixel e ne disegna il profilo con un
  gradiente verticale (`glassRimRamp`: la rampa lunga un angolo e mezzo, mezza altezza per una capsula).

La trappola è il giro, e vale la pena scriverla: **girare il solo colore compila, gira, e non disegna
niente.** Un bordo scuro in `Plus` si somma a niente e sparisce; il lato chiaro sono due metà insieme,
un colore scuro *e* un modo ordinario di posarlo. Chi disegna un bordo di vetro lo chiede a
`glassRimRecipe` e prende le due cose.

Due conseguenze, che sono la stessa regola vista dal lato dell'ombra:

- **niente ombra interna insieme a un bordo speculare**: sono due trattamenti dello stesso millimetro
  di perimetro, e insieme si leggono come due bordi invece che come spessore (`panelOptics` la tiene a
  zero per questo);
- **l'ombra esterna è del vetro, mai di piattaforma.** Un `Modifier.shadow` su una superficie
  trasparente trapela dentro la sagoma, e non ha il colore del lato del tema né il peso del ruolo. Il
  colore è `glassShadowColor` (nero al 16% sul chiaro, al 10% sullo scuro) e il peso quello del ruolo;
  per un pannello che non è lui a disegnare la propria ombra (un antenato che la ritaglia: la barra
  flottante) c'è `glassShadow`, e per la finestra di Fluid-physics `fluidPhysicsShadow`. La finestra che
  si espande ha oggi un'ombra circa sei volte più leggera di quella di prima: è il numero da giudicare
  in mano, e sta in una costante sola.

Provato da `GlassRimRecipeTest` (la ricetta per lato, il giro nelle sue due metà, la rampa contro lo
shader: una capsula coincide esattamente, un angolo di card entro pochi punti percento, e il colore
dell'ombra è lo stesso conto per chiunque ne disegni una).

## La goccia e la lastra (3.0)

Il vetro dell'engine ha **due famiglie**, e ogni elemento è dell'una o dell'altra: la ricetta di
ciascuna sta in `docs/10-coerenza.md`.

- **La lastra** è ciò che sta: pannelli, barre, card, gruppi, menu, i tasti di vetro a riposo.
  `glassSurface`, `glassControlSurface` o `fluidPhysicsSurface`, con ottica dal ruolo, bordo e ombra del
  vetro.
- **La goccia** è ciò che si afferra e scorre: i pomelli di slider e interruttore, la pillola della tab
  bar, quella del segmentato, la goccia dei menu. A riposo è **piena** (bianca nel chiaro); sotto il dito
  **diventa lente**: sfocatura `8 dp × (1 − p)`, lente che sale con la pressione, ombra interna solo
  premuta. È la ricetta di Kyant, e sta in `glass/interaction/GlassDrop.kt` (`glassDrop`,
  `glassDropSquash`), una volta sola: il pomello dello slider e quello dell'interruttore ne avevano una
  copia ciascuno, la pillola della barra una scritta a mano.

Non si mescolano. Una goccia non prende l'ottica di un ruolo (`GlassRole.Interactive` non è la goccia:
è quasi trasparente con la lente al massimo, e la goccia piena a riposo non l'ha mai letta), e una
lastra non si schiaccia con la velocità: **lo squash è della goccia**, dalla velocità della molla,
`/10`, tetto 0,2, e non esiste per un pannello.

Le eccezioni sono tre, hanno un nome, e stanno tutte nel verso del metro (R1: slider, interruttore e
pillola cambiano solo verso i riferimenti, mai allontanandosene):

- l'**interruttore** tiene i numeri del port di Kyant, squash **con segno** e `/50`
  (`SwitchSquashDivisor`, contro il `SquashDivisor` di 10 degli altri): la sua corsa è di venti dp, e
  la velocità della molla in intervalli al secondo è molto più alta di quella di uno slider;
- la **pillola della tab bar** tiene lo squash **con segno**, `/10`, come aveva: la ricetta dice
  assoluto, la pillola ha sempre fatto col segno, e cambiarlo sarebbe cambiare il metro;
- l'interruttore e la pillola **non guardano le animazioni ridotte** per lo squash: erano così, e un
  metro non si cambia per far tornare un conto. Lo slider le guarda, e la ricetta le guarda.

`GlassDropSquashTest` rifà il conto *come stava scritto prima* per i tre e lo confronta bit per bit con
la funzione condivisa: un'ultima cifra spostata sarebbe un metro cambiato.

## Un solo vocabolario di movimento

Tutto passa da `FluidMotion`. `FluidMotionScheme` lo passa anche ai componenti Material, così uno
switch e la chrome custom si muovono con le stesse molle.

Due invarianti:

- **Una pagina in arrivo non sfuma mai.** Le pagine sono opache (da destra di serie) e quella che si
  muove è una carta che sta sotto il dito (vedi «Le pagine non sfumano e seguono il dito»): due pagine
  leggibili sovrapposte, anche per 100 ms, sono la cosa che fa sembrare l'app un prototipo.
- **`FluidMotionPolicy` va rispettata.** Quando la scala di animazione di sistema è a zero, il
  movimento decorativo sparisce. È un'impostazione di accessibilità, non un suggerimento.

Una `tween` scritta a mano dentro una schermata è il modo più rapido per far sembrare quella
schermata di un'altra app.

## Le pagine non sfumano e seguono il dito (3.0)

Una pagina è la cosa più grande che si muove, e le regole sono due.

**Opaca dal primo all'ultimo fotogramma.** Nessuna pagina sfuma, nessuna sfoca quella sotto, nessuno
scrim: due pagine leggibili una attraverso l'altra, anche per 100 ms, sono la cosa che fa sembrare
l'app un prototipo, e una sfocatura a tutto schermo è il fotogramma più caro che l'app possa disegnare
proprio quando ne ha meno. Il vecchio involucro delle rotte (`FluidRouteMotionHost`) teneva opaca la
pagina che entrava arrotondandola a 30 dp mentre cresceva, e sfocava di 6 dp quella che usciva: un blur
a tutto schermo per fotogramma, il costo più alto di un cambio di rotta. Con `FluidNavHost` non serve:
la pagina che si muove è una **carta**, con gli angoli del telefono (dove l'host tocca davvero un
angolo dello schermo) e un'ombra da 36 dp, e una carta non ha niente da nascondere. A riposo il layer
non ritaglia e non proietta niente. Fa eccezione, come sempre, il passo fra due schede,
che non è una pagina: 13% della larghezza, opaca sopra, e la vecchia scivola e sfuma in 150 ms; quella
che arriva non sfuma mai. Con le animazioni ridotte i moti autonomi saltano, e il dito segue comunque
1:1.

**Sotto il dito, pixel per pixel, finché il dito non la lascia.** Il back è quello di iOS: la pagina
sta sulla **posizione vera** del dito (`touchX`), non sul `progress` del gesto di sistema. Il perché è
il difetto che ha fatto nascere l'host: `NavHost` cerca la transizione sul `progress`, che il sistema
smussa con una molla sua e scala sulla propria distanza massima, e poi lo passa per un easing. Con
`EaseEmphasized`, `W · ease(p)` a un quinto del gesto (p = 0,20) porta la pagina a mezzo schermo (0,50 W):
**due volte e mezzo il dito** (0,6× a p = 0,05, 1,6× a 0,10, 2,3× a 0,30). Una pagina che corre davanti al
dito non è fluida, è scollegata, ed è come tornavano indietro FluidWeather, PampAI e Pampa widgets. Il
dito vero non ha questo problema, e il sistema lo dà a ogni evento. Ne seguono le altre scelte:

- **la molla parte dalla velocità del dito**, mai un `tween` di durata fissa: al rilascio la velocità
  che il dito aveva non si butta, e la pagina continua il suo gesto invece di cominciarne un altro.
  Criticamente smorzata: una pagina non rimbalza;
- **il rilascio**: conferma oltre il 35% della larghezza (con un tetto di 180 dp, perché su un tablet
  il 35% è mezzo braccio) o con una spinta di 400 dp/s; una spinta contraria della stessa forza annulla
  anche oltre la distanza, perché il dito ha cambiato idea e la molla gli dà ragione;
- **l'annullo del sistema si deduce.** Il sistema annulla un back con ~200 ms di ritardo: prima riporta
  il suo `progress` a zero con un'animazione sua, a dito già alzato. Due eventi col dito fermo e il
  progresso in calo vogliono dire che il dito non c'è più, e la pagina parte subito;
- **il back di sistema passa dal dispatcher di navigationevent**, registrato prima delle pagine: i
  `BackHandler` dell'app restano davanti a lui, e un'app che ne ha uno (la selezione, un modale) non lo
  perde;
- **un gesto confermato chiama `onBack()` prima della molla**, che continua mentre la pila si accorcia:
  l'app non deve sapere che c'è una molla.

**Da qualunque punto, e i tasti non consumano.** `FluidBackSwipe.Anywhere` è di serie, come LibrePods:
un figlio che consuma (un cursore, un interruttore, una lista che scorre di lato) vince, e uno
scorrimento orizzontale arrivato al suo inizio passa la mano alla pagina. Poiché dalla 3.0 un tasto non
prende mai il gesto, un trascinamento orizzontale che parte su un tasto fa partire il back: coerente con
LibrePods, **da giudicare in mano**. `EdgeOnly` (i primi 20 dp) per le pagine piene di gesti propri,
`Off`: mai col dito dentro l'app, restano il back di sistema e il tasto. Nessuna vibrazione nel gesto:
una pagina che scorre non è un momento da sentire.

Il codice è `ui/page/` e `ui/navigation/`; la ricetta di migrazione sta in `docs/03-design-system.md`.
`FluidRouteMotionHost` resta com'era per chi non migra, e sfoca ancora: dentro un `FluidNavHost` non va
messo (vuole un `AnimatedVisibilityScope` che le pagine Fluid non hanno).

## Una vibrazione per momento (e l'eccezione dei tasti)

Due componenti che dicono lo stesso istante non vibrano due volte: **due eventi di controllo entro 70 ms
sono lo stesso momento**, e il secondo non suona. Era il «toc-toc» che faceva sembrare un errore
l'apertura di un menu: due componenti dicevano lo stesso istante, e il secondo colpo non aveva un
motivo. La rete è nel motore (`FluidHaptics`), non nel call-site: nessun componente deve sapere cosa
suona il suo vicino.

- **Quali si fondono**: Tap, Confirm, Reject, ToggleOn/Off, Threshold, Open, Close, GestureStart/End.
  **Quali no**: esiti e allerte (`Success`, `Warning`, `Error`, `Alert*`) non si scartano mai, e i tick
  continui (`Tick`, `FrequentTick`, `WaitTick`, `DragTick`) hanno il loro cancello a 40 ms e non armano
  la rete.
- **`Open` è un colpo solo** (un click pieno a 0,6), non una rampa più un tick.
- **Un tasto che apre qualcosa di suo** passa il proprio evento a `fluidPressable(haptic = ...)` al
  posto del click; `haptic = null` spegne tutto, grana compresa. Una **riga che è l'interruttore**
  (`FluidToggleRow`) suona `ToggleOn`/`ToggleOff` e non `Tap`: la pressione è di
  `fluidRowPressable(haptic = null)`, perché fondere i due sceglie il primo, cioè quello sbagliato. Una
  riga di scelta (`FluidChoiceRow`) suona un `Tick` solo se la scelta si sposta.

**L'eccezione è una, voluta, ed è dei tasti.** Un tasto che cresce (`FluidPressFeedback.Expand`) fa la
coreografia di LibrePods, con le costanti di sistema: `PressDown` (`SegmentFrequentTick`) al contatto,
`PressRelease` (`Reject`) al rilascio, `PressClick` (`ContextClick`) al click. Un tap fa quindi **tre
colpi**, e uno trascinato veloce ne fa uno per ogni evento sopra la soglia (la grana, stile
`LibrePods`). Sono eventi **solo di piattaforma** e non fondibili, e non armano la rete dei 70 ms: se
la armassero, il click di un tap sarebbe scartato dal suo stesso contatto. Il motivo per cui è l'unica
eccezione è che lì la coreografia *è* il tasto: in mano è la differenza che si sente fra un tasto che
risponde e uno che si limita a scattare. Ogni altro componente resta a un momento, un colpo.

Provato da `FluidHapticsTest` (la rete, `Open`, i tre colpi in coda al vocabolario, solo di piattaforma,
non fondibili) e da `FluidButtonDragTest` (un tap suona esattamente contatto, rilascio e click, in
quest'ordine; una superficie che cede fa ancora un `Tap` solo).

## L'engine non conosce il dominio

`engine-foundation` non importa niente di Compose né di Android UI. Nessun modulo dell'engine sa cosa
sia un voto, una materia, una lezione.

Quando un componente sembra generico ma ha un parametro che nomina un concetto dell'app, non lo è:
generalizza il parametro o lasciarlo nell'app. `SyncStatus.notice` è l'esempio: nell'app da cui
l'engine viene si chiamava `schoolYearNotStarted`, e generalizzarlo in "una frase che spiega perché
questo non è un errore" lo ha reso utile a qualsiasi app che sincronizzi qualcosa.

## Il codice dentro `engine/` non è codice dell'app

È un repo git a sé, agganciato a un tag. Modificarlo dentro un'app senza committarlo nel repo
dell'engine produce una variante che:

- sparisce al primo `engine-update.ps1` senza avviso;
- non arriva a nessun'altra app;
- fa sembrare che il bug sia stato risolto quando non lo è.

`engine-doctor.ps1` segnala le modifiche non committate proprio per questo.

## Il morphing vive in un posto solo

La regola storica — niente shape morphing, un raggio animato ri-clippa ogni fotogramma — resta
vera per le schermate. Dalla 1.9.0 esiste la deroga, ed è una sola: **Fluid-physics**
(`ui/fluidphysics`), che se la guadagna con una disciplina propria: il clip del layer non insegue
mai la sagoma (la silhouette la scolpisce l'alpha dello shader), il padding della cattura resta
fermo per tutto il viaggio, e a riposo la superficie tiene un'istanza di `Shape` stabile — il
morphing è transiente per contratto. Un raggio animato a mano in una schermata è ancora il bug di
prima; una forma che deve trasformarsi chiede un `FluidPhysicsState`.

**Un viaggio che si cancella si ferma dove si trova (3.0).** Se il viaggio viene interrotto a metà
(`morphTo` cancellato: il menu che si chiude mentre si sta ancora aprendo), la forma resta quella
interpolata in quell'istante, e il `morphTo` successivo parte da lì. Prima saltava al traguardo per un
fotogramma — il menu chiuso a metà apertura compariva come pannello pieno — e il test che lo avrebbe
visto (`FluidPhysicsCancelTest`) non era mai stato eseguito. Vale per ogni superficie che si trasforma,
non solo per i menu: l'inversione a metà viaggio dal punto in cui si è è un requisito della ricetta del
pannello in movimento (`docs/10-coerenza.md`), e un fotogramma che salta al traguardo la rompe.

Vale anche per gli shader: "nessuno shader di vetro nuovo accanto a GlassMaterial" resta la
regola, e la famiglia SDF di Fluid-physics è la deroga deliberata, nel suo package, annotata in
`LICENSES/AndroidLiquidGlass.md` (il preludio deriva da quello Apache-2.0 di Kyant).

## A riposo una superficie fisica è una lastra (3.0)

Sfocatura, lente, cupola, frangia e pressione escono da `physicsLensUniforms`, gli stessi numeri di
`glassSurface`; bordo e ombra sono registrati con la ricetta del renderer. Prima la superficie fisica non
aveva ombra e il suo bordo era un tratto netto mezzo fuori dalla sagoma: era il motivo per cui non sembrava
vetro come la pillola. In viaggio gli stessi numeri seguono la sagoma: niente scalino alla partenza né
all'arrivo. Il materiale che cambia (`opticsFrom`) si sfuma con `lerpGlassOptics`, e il margine della cattura
resta zero per costruzione (sotto un quarto di pixel è zero: l'arrotondamento non deve rifare la cattura).
L'oltrepasso è un gonfiore attorno al centro del traguardo, mai il lerp oltre l'1, e la molla parte dalla
velocità del momento: niente rincorse (la rincorsa della casa è il preset `FluidPhysicsJourney.House`).

## Tre trappole del vetro in movimento

Pagate una per una sui fotogrammi, migrando la card che si espande (1.9.7). Chi tocca una
superficie di vetro animata le incontra tutte e tre.

1. **Non scalare mai il layer di una superficie di vetro per farle fare un rimbalzo.** Un
   `graphicsLayer { scaleX = … }` scala anche il fondale campionato dentro il vetro: il testo della
   pagina *dietro* si muove, e non ha senso — quel testo sta fermo. L'oltrepasso va nella
   geometria della sagoma (`overshootInflation`), non nella trasformazione del nodo.
2. **Il primo e l'ultimo fotogramma di vita di un nodo possono disegnare con le proprietà di
   default.** Su una superficie il cui arrivo è un alpha (scrim, finestra dei modali) questo è un
   lampo a piena forza — misurato: un fotogramma a 190 di luminanza in mezzo a una serie a 214. Il
   cancello è `drawWithContent { if (amount > 0.004f) drawContent() }`: a materiale zero non si
   registra niente, e una displaylist vuota non ha lampi da rigiocare.
3. **Gli `exports` di uno scrim registrano il suo materiale a forza PIENA**, perché il suo arrivo
   è un alpha sul pannello finito. Chi campiona quel composito resta scuro mentre lo scrim vero
   sfuma. Meglio campionare la pagina cruda e dipingersi la propria quota di scurimento alla forza
   corrente (`tintFrom` + `tintBlend`).

E il metodo, perché nessuna delle tre si vede "ragionando": si vedono nei fotogrammi. Video con
`screenrecord`, estrazione con **`-fps_mode passthrough`** (`fps=30` su un video a frame rate
variabile riordina e duplica i fotogrammi, e una cronologia sbagliata porta a diagnosi sbagliate),
e per i difetti di un fotogramma solo la misura batte l'occhio: `signalstats,metadata=print` dà la
luminanza per frame, e un lampo è un picco isolato in una serie che dovrebbe essere monotona.
