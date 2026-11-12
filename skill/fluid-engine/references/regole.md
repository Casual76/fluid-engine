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

Il contrasto è verificato da `FluidContrastTest`. Se aggiungi un ruolo, aggiungi il caso. **Il vetro segue il
contrasto di sistema** (Android 14+): pellicola e ripiego più densi, bordo più netto, sempre dalle pellicole
di `GlassDefaults` (una tinta scritta a mano non lo segue); si forza con `LocalFluidSystemContrast` dentro il
tema.

## Liste raggruppate

Righe dentro un unico contenitore arrotondato, separate da hairline da 0.5dp rientrate dove comincia
il testo.

Il tono della riga sta sulla **piastrella dell'icona**, mai sullo sfondo della riga. Colorare le
righe trasforma un gruppo ordinato in un patchwork di blocchi colorati: era così prima, ed è la cosa
che rendeva l'app "colorata" invece che leggibile.

Il feedback al tocco per una riga è `fluidRowPressable` (tinta), non `fluidPressable` (scala): una
riga che si rimpicciolisce rompe la sagoma del gruppo e fa sembrare la lista instabile.

Per TalkBack una riga è **un nodo solo**. Con `onClick` ha il nome (titolo e sottotitolo, fusi), `Role.Button` e `selected`
se è quella mostrata accanto; senza è un'etichetta, senza ruolo. Il `ListItem` di Material fonde da sé la propria semantica
e il `clickable` fonde tutto in un nodo, quindi non ne restano due (`FluidMatrixTest` lo prova su un albero vero): chi
aggiunge un modificatore di semantica sul contenuto di una riga lo fa sul nodo della riga, mai su un discendente.

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
- **il pulsante tiene il dito.** Un dito che scende su un pulsante è del pulsante fino al
  sollevamento: la pagina sotto (`LazyColumn`, `verticalScroll`, un pager, il back a scorrimento) non
  scorre, il pulsante cresce, segue il dito e vibra; per scorrere si parte fuori dai pulsanti. È la
  regola della 2.12 **senza i 100 ms di attesa** (`FluidPressGrip.HoldsFinger`, di serie): il gesto
  consuma gli spostamenti nel passaggio Main, prima di ogni antenato. Scelta di Alessio dopo la beta 2:
  con LibrePods (il pulsante non prendeva mai il gesto) la pagina scorreva mentre si teneva un tasto.
  **Il chip e il pallino di colore cedono il dito alla fila** (`grip = FluidPressGrip.YieldsToScroll`):
  stanno in file che scorrono di lato, e appena la fila prende il gesto si sciolgono e non cliccano. Righe e card non cambiano. **Un pulsante ripetuto su ogni riga di una lista che scorre, o in un pager, passa
  `grip = YieldsToScroll`**: lì non c'è un fuori da cui partire. I tondi crescono ×1,5 fino a 48 dp;
  oltre, di 24 dp in tutto (12 per lato); le misure libere con `FluidGlassIconButton(size = …dp)`;
- **il click**: di `clickable` per un tocco, del gesto per un dito che ha trascinato (`clickable`
  annulla alla vista di un cambio consumato). Scatta se il dito si solleva sul pulsante più 16 dp
  (`FluidPressClickSlack`), anche dopo aver vagato, con lo stesso `PressClick`; oltre il margine suona
  solo `PressRelease`. Tastiera, fuoco, TalkBack e ruolo restano di `clickable`. Il **click lungo resta** (ai menu serve): scatta se il dito è rimasto entro lo
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
  `fluidPressable(haptic = ...)` al posto di `PressClick` (un tasto che apre il menu *al tocco*, `Open`;
  uno che lo apre tenuto premuto resta `PressClick` al tocco, e il click lungo ha il suo `longClickHaptic`,
  `null` se il colpo lo suona il menu); `haptic = null` spegne tutto;
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
fondo. La tinta interna `glassFilm()` di `GlassDefaults` (non pubblica) parte da due grigi fissi e prende solo un terzo della palette,
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
  `fluidPhysicsSurface` col vetro di prima non può chiedere la normale a ogni pixel e ne disegna il
  profilo con un gradiente verticale (`glassRimRamp`: la rampa lunga un angolo e mezzo, mezza altezza
  per una capsula).

**Dalla 4.0, col materiale nuovo, il bordo è uno per costruzione.** Filo e riflesso li dà il profilo
(`GlassLiquidProfile`) e li disegna lo stesso programma di luce per tutte le strade: `glassSurface` sul
nodo, Fluid-physics dentro il corpo sulla sagoma di ogni fotogramma (ponti compresi, niente rampa), la
pillola della tab bar e la goccia attraverso i loro profili derivati, il vetro dipinto con le stesse
formule su tratti. `glassRimRecipe` e `glassRimRamp` restano del vetro di prima. Il filo del vetro
nuovo è **scuro** sul chiaro e **chiaro** sullo scuro come qui sopra; i suoi numeri non si scrivono
fuori dal profilo (la regola «i numeri di gusto stanno nel profilo», sotto).

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
  flottante) c'è `glassShadow`, e per la finestra di Fluid-physics, col vetro di prima,
  `fluidPhysicsShadow`. Col materiale nuovo l'ombra di Fluid-physics è nel corpo, una sola anche sui
  ponti, e `fluidPhysicsShadow` e `PhysicsShadowLayers` non si montano.

Provato da `GlassRimRecipeTest` (la ricetta per lato, il giro nelle sue due metà, la rampa contro lo
shader: una capsula coincide esattamente, un angolo di card entro pochi punti percento, e il colore
dell'ombra è lo stesso conto per chiunque ne disegni una) e, per il materiale nuovo, da
`GlassRecipeParityTest` (filo, riflesso e ombra arrivano uguali da ogni strada).

**Come si verifica in un'app.** Nel codice: nessun `Modifier.shadow`, `border(` o `drawBehind` con un
tratto chiaro o scuro su una superficie che porta `glassSurface`, `glassControlSurface`,
`fluidPhysicsSurface` o `fluidStaticGlassSurface` (`grep -n "\.shadow(\|\.border(" ` accanto a quei
modificatori). A schermo: un tasto e un menu aperto dallo stesso tasto hanno lo stesso filo.
**Se la violi.** Due bordi sulla stessa lastra (il filo del vetro più il tuo, a un pixel l'uno
dall'altro), o un'ombra di piattaforma che trapela dentro la sagoma come una macchia grigia.

## La goccia e la lastra (3.0)

Il vetro dell'engine ha **due famiglie**, e ogni elemento è dell'una o dell'altra: la ricetta di
ciascuna sta in `docs/10-coerenza.md`.

- **La lastra** è ciò che sta: pannelli, barre, card, gruppi, menu, i tasti di vetro a riposo.
  `glassSurface`, `glassControlSurface` o `fluidPhysicsSurface`, con ottica dal ruolo, bordo e ombra del
  vetro.
- **La goccia** è ciò che si afferra e scorre: i pomelli di slider e interruttore, la pillola del
  segmentato, l'indicatore del rail, la lente dell'indice, la goccia delle voci dei menu. A riposo è
  **piena** (bianca nel chiaro); sotto il dito **diventa lente**. Sta in `glass/interaction/GlassDrop.kt`
  (`glassDrop`, `glassDropSquash`), **una funzione sola per i controlli e per le voci dei menu**: dalla
  4.0 anche la goccia del menu (`FluidMenuDrop`) chiama `glassDrop`, dove prima ne aveva una copia.
  Geometria e tempi «faccia piena → lente» sono quelli di sempre. Il vetro sotto la faccia dipende dal
  materiale: **col materiale nuovo** (da Android 13, `Liquid`) è la ricetta dei tasti attraverso il
  profilo `GlassDropProfile`, derivato da quello di serie: a riposo è la faccia piena con **l'ombra di
  un tasto** (ridotta dalla regola del vetro annidato dentro una lastra o un menu), **senza filo né
  riflesso**: arrivano con la lente, in proporzione alla pressione, e a pressione piena sono quelli dei
  tasti. Sotto il dito resta una **lente limpida**, senza velo né smerigliatura, con un tono scuro
  leggero al posto del velo, che piega forte al bordo e cresce con la pressione. **Col vetro di prima** è
  la ricetta di Kyant: sfocatura `8 dp × (1 − p)`, lente che sale con la pressione, ombra interna solo
  premuta.

La pillola della **tab bar** non è più di questa famiglia dal 4.0: col materiale nuovo è vetro della
barra un po' più scuro (`GlassPuckProfile`, «La scheda scelta la dicono la pillola e l'accento», sotto),
e tiene della goccia lo squash e il viaggio.

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

**Una goccia in una lastra ha la faccia del tema** (fase 9). Il bianco fisso è dei pomelli, che stanno su una pista
colorata e di cui è l'identità; il segmentato, il rail e la lente dell'indice stanno in una lastra, e lì la faccia è
`GlassDropFaces.forSurface(isDarkSurface())`: bianca sul chiaro, il grigio di iOS (0x636366) sullo scuro, perché un
bianco pieno su una pista scura è un faro e l'etichetta `onSurface` che ci sta sopra non si leggerebbe. È sempre
`glassDrop`, un modificatore solo con la faccia e il movimento come parametri (una `GlassDragAnimation`, o le lambda di
`GlassDropMotion` per la lente dell'indice, la cui pressione è l'apertura del nastro): prima c'era un secondo
modificatore, `glassDropFace`, e due ricette che dicevano la stessa cosa sono due ricette che divergono. L'alfa della faccia
bianca è `1 − p` bit per bit come nei pomelli (`GlassDropFaceTest`).

**La goccia, come si verifica.** Nel codice: nessun pomello, cursore o pillola scritto a mano con `drawBackdrop`,
`graphicsLayer` e un cerchio bianco (`grep -n "drawBackdrop(" ` fuori da `engine/`); i controlli dell'engine
(`FluidSwitch`, `FluidSlider`, `FluidSegmentedControl`) passano da `glassDrop` da soli. A schermo, da Android 13: il
pomello a riposo ha la stessa ombra del tasto di vetro accanto e nessun filo (pagina «Parità» della galleria), il
filo e il riflesso del tasto arrivano premendo, e sotto il dito il fondo si vede limpido, piegato al bordo, mai
smerigliato. **Se la violi.** Un pomello di un altro vetro accanto ai tasti (con un'ombra propria, o un filo a riposo
che i tasti non hanno), o una lente grigia e sfocata sotto il dito: il «secondo
vetro» che il profilo della goccia toglie. Provato da `GlassDropProfileTest`, `LiquidDropTest`, `DropPlanTest`,
`GlassDropSquashTest`, `GlassDropFaceTest` e, contro il profilo di serie, da `GlassRecipeParityTest`.

**Un ingresso non scala un vetro.** L'indice di sezione, la sua bolla, il suggerimento e, dalla 4.0, la **notifica**
entrano per offset di layout, alfa o crescita della sagoma, mai per `scaleIn` su un antenato del vetro: un layer scalato
sopra un vetro rifrange ciò che non c'è più (è la prima delle tre trappole in fondo), e un `AnimatedVisibility` con
`scaleIn` attorno a una pista di vetro la fa "respirare" a ogni comparsa. La notifica lo faceva fino alla 4.0
(`scaleIn(0,97)` e `scaleOut` sull'antenato del suo vetro); ora la comparsa da 0,97 e l'uscita a 0,985 sono la sagoma
della card che cresce e cala per misura, con la stessa molla, e il contenuto resta della sua misura
(`FluidNotificationShapeTest`).

**Come si verifica.** Nel codice: `grep -n "scaleIn\|scaleOut\|scaleX\|scaleY" ` nei file che compongono un vetro
(`glassSurface`, `fluidPhysicsSurface`, un componente dell'engine dentro un `AnimatedVisibility`): nessuna scala su un
antenato del vetro. A schermo, a fotogrammi: durante la comparsa il testo **dietro** il vetro sta fermo. **Se la violi.**
Il fondo dietro il vetro si ingrandisce e si sposta per il tempo dell'ingresso (il vetro sembra una lente d'ingrandimento
che respira), e il bordo del vetro si sfuoca mentre scala.

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

**Dalla fase 10 vale per tutto, anche le molle.** Ogni numero di movimento ha un nome in `FluidMotion` — le molle della
goccia di Kyant (`dropValue`, `DropPress`, `DropScaleX`/`Y`), i tempi del pop-up (`Popover*`), il ritorno del puck
(`TabTugReturn`), il respiro (`pulse`, `PulseMillis`), la molla di serie che `animate*AsState` usava senza dirlo (`flick`),
le molle che muovono pixel (`pixels`, soglia mezzo pixel) — **con gli stessi valori di prima**: lo prova
`FluidMotionTokensTest`, che rifà l'espressione come stava scritta nel componente e confronta la traiettoria a ogni passo di
25 ms, perché per la pillola, lo slider e l'interruttore un numero spostato è un metro cambiato (R1). Un grep di
`spring(`, `tween(` e `keyframes` fuori da `FluidMotion.kt`, dal renderer (`glass/backdrop/**`), da Fluid-physics, dalle
molle dei tasti (`FluidPressSprings`, costanti di LibrePods) e dalle pagine non trova niente, e deve continuare a non
trovare niente: un movimento nuovo si aggiunge a `FluidMotion`, non al file del componente.

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
- **l'annullo del sistema si deduce solo nel punto più lontano del gesto (3.0.4).** Il sistema (Android
  14) annulla un back con ~200 ms di ritardo: prima riporta il suo `progress` a zero con un'animazione
  sua, a dito già alzato. Ma riporta il `progress` a zero anche **col dito giù**: quando il dito torna
  verso il bordo (misurato sull'emulatore: da 0,53 a 0 in sei fotogrammi, `touchX` fermo), e dopo un
  passo indietro o il tremolio di un pollice la molla che lo smussa scende per qualche fotogramma a dito
  immobile. Fino alla 3.0.3 un evento in calo col dito fermo bastava: la pagina partiva verso casa e il
  movimento dopo la rimetteva sotto il dito, lo scatto «come indecisa». Ora l'annullo si deduce solo se
  il progresso scende per due eventi col dito fermo nel punto più lontano che ha raggiunto, dove finché
  il dito è giù il progresso può solo salire; altrove la pagina resta sotto il dito e aspetta il sistema.
  Se il dito c'era, la pagina si riprende da dov'è, senza saltare;
- **un dito, un pilota (3.0.4).** Con la navigazione a gesti l'app vede il dito del bordo finché il
  sistema non se lo prende: il back di sistema prende la pagina da dove l'ha portata il riconoscitore
  dell'app (o il ritorno di un annullo), non la fa saltare sotto il dito;
- **il back di sistema passa dal dispatcher di navigationevent**, registrato prima delle pagine: i
  `BackHandler` dell'app restano davanti a lui, e un'app che ne ha uno (la selezione, un modale) non lo perde;
- **anche il back a dito passa dal dispatcher.** Oltre la soglia il rilascio manda lo stesso «indietro» del
  sistema: un `BackHandler` abilitato dopo l'host risponde per primo, la pagina torna sotto il dito con la
  molla dell'annullo e il gestore decide; senza gestori risponde l'host e la pagina esce. Sotto la soglia
  nessun gestore gira. Non si spegne più il gesto per una conferma di uscita;
- **un gesto confermato chiama `onBack()` prima della molla**, che continua mentre la pila si accorcia:
  l'app non deve sapere che c'è una molla.

**Il back a dito parte dal bordo, come quello di sistema (3.0.3).** `FluidBackSwipe.EdgeOnly` (i primi 20 dp
del bordo d'ingresso) è di serie, in ogni punto in cui c'è un default (`composable(...)`, `FluidPageHost`,
`FluidNavHost`): righe, card, tasti, segmentati e scorrimenti in mezzo alla pagina non c'entrano col back, e non
serve più sapere chi consuma il dito. `Anywhere` è un'opzione esplicita, per la pagina che lo vuole davvero (era
di serie dalla 3.0.0 alla 3.0.2, come LibrePods): lì **chi consuma vince** — un tasto, il segmentato anche dal
segmento scelto, una riga o card cliccabile, un cursore, un interruttore, una lista che scorre di lato — e il
back parte dove nessuno lo prende; uno scorrimento orizzontale arrivato al suo inizio passa la mano alla pagina.
Un trascinamento orizzontale che parte su un tasto non porta indietro nemmeno così: il tasto tiene il dito (e
`clickable` consuma già il down, che il riconoscitore legge come "un figlio se l'è preso"). `Off`: mai col dito
dentro l'app, restano il back di sistema e il tasto. Nessuna vibrazione nel gesto: una pagina che scorre non è
un momento da sentire. Un'app che aveva messo `EdgeOnly` per i suoi gesti propri (mappe, pager, griglie in
selezione) può toglierlo.

**Chi dipinge il fondo dice il colore del testo (3.0.1).** Ogni host alla radice (modali, menu, notifica,
suggerimenti, `FluidPageChrome`, le pagine dell'host) fornisce `LocalContentColor` dal suo fondo; un host nuovo
fa lo stesso (`FluidHostContentColorTest`). Un testo senza colore esplicito non esce mai nero sul nero.

**Le parole dell'engine sono dell'app (3.0.1).** Le etichette che l'engine dice da sé (occhio del campo
segreto, chiudi, indietro, «Torna all'inizio»…) vengono da `LocalFluidLabels`, italiano di serie: un'app
tradotta le fornisce in radice (`FluidLabels.english()` per l'inglese, dalla 3.0.4, con le frasi della sincronizzazione);
un componente nuovo non scrive stringhe fisse, aggiunge una voce lì (e la sua traduzione in `english()`: un test lo verifica).

Il codice è `ui/page/` e `ui/navigation/`; la ricetta di migrazione sta in `docs/03-design-system.md`.
`FluidRouteMotionHost` resta, deprecato, per chi non migra (non sfoca più né arrotonda): dentro un `FluidNavHost` non va
messo (vuole un `AnimatedVisibilityScope` che le pagine Fluid non hanno).

## Le pagine: una barra per host (3.0)

Dentro un host di pagine la barra la disegna l'host: una pagina non si disegna una barra sua sopra il contenuto (si
vedrebbero due barre, e la sua viaggerebbe col dito). Il titolo grande è contenuto e viaggia con la pagina; il titolo
agganciato, il vetro e il tasto indietro sono chrome e stanno fermi. Si tocca solo a riposo: un gesto partito sulla
barra è della barra, mai il back. Nessuna pagina sfuma né si sfoca.

Il perché, una cosa per volta:

- **una per host, descritta dalla pagina.** `FluidScreen` e `FluidCollapsingTopBar` non disegnano la barra quando c'è un
  host: la *registrano* (titolo, azioni, i numeri del titolo agganciato) e l'host ne disegna una sola, sopra le pagine e
  sotto la chrome dell'app, leggendo tutto in disegno. Fuori da un host disegnano quella di sempre, quindi nessuna firma
  è cambiata; per una pagina che non è una `FluidScreen` c'è `FluidPageChrome`;
- **il vetro vede le due pagine insieme.** Mentre le pagine si muovono la barra rifrange la registrazione di tutte e due,
  ognuna al suo offset vivo, e lo stesso impilamento va alla chrome dell'app (la tab bar, con
  `FluidChromeController.setTransitionBackdrop`): una barra che rifrangesse una pagina sola mostrerebbe, sotto il
  vetro, una pagina che sta per essere coperta;
- **i tasti di vetro passano a quelli della pagina accanto.** Durante il moto il vetro del singolo tasto si spegne e una
  superficie sola di Fluid-physics, guidata da fuori (`FluidPhysicsState.driveExternally(from, to, progress)` fra gruppi di
  lastre), porta i pezzi della pagina che esce su quelli della pagina che arriva; il tasto indietro radice↔figlia compreso.
  Oltre sei pezzi, o con un controllo che non è una capsula di vetro, le icone si incrociano e basta;
- **chi ha il dito.** Un gesto che parte sulla riga dei controlli della barra è della barra (serve anche al menu delle
  azioni, che si sceglie trascinando); il back parte dal bordo (3.0.3); con `Anywhere` i tasti dentro la pagina lo lasciano partire dal vuoto. Uno
  scroller orizzontale al suo inizio passa il gesto al back (solo con `Anywhere`); uno slider non lo passa mai.

Provato da `FluidPageHostTest` (il tasto indietro è uno e sta fermo al pixel, il titolo grande viaggia con la sua pagina,
un gesto che parte sulla barra è della barra, un cursore non porta mai indietro), `FluidPageMotionTest` (il morph dei
tasti, le icone che non si leggono insieme, l'accoppiamento dei pezzi), `ExternalGroupDriveTest` e
`FluidChromeTransitionTest`. Quello che nessun test dice è com'è il passaggio dal vetro del tasto alla superficie fisica
(un lampo di un fotogramma?), il titolo grande in `ShrinkSlide`, la fluidità della ricattura e il back di sistema su One UI.

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
  posto del click; `haptic = null` spegne tutto, grana compresa. Il **click lungo** ha il suo colpo
  (`longClickHaptic`, di serie `GestureStart`; `null` per chi ne ha uno suo, come un menu che suona `Open`
  solo se si apre) e `combinedClickable` non somma più il `LongPress` di piattaforma
  (`hapticFeedbackEnabled = false`): una riga con un menu contestuale suonava tre colpi per un momento. Una **riga che è l'interruttore**
  (`FluidToggleRow`) suona `ToggleOn`/`ToggleOff` e non `Tap`: la pressione è di
  `fluidRowPressable(haptic = null)`, perché fondere i due sceglie il primo, cioè quello sbagliato. Una
  riga di scelta (`FluidChoiceRow`) suona un `Tick` solo se la scelta si sposta. Un gesto che comincia con un colpo suo
(la pressione lunga che apre l'indice di sezione: `GestureStart`) non suona un `Tick` al primo evento: il primo evento non è
una tacca attraversata, e riselezionare la sezione dov'è già sarebbe un secondo momento per lo stesso dito.

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

## Un menu si sceglie col dito che l'ha aperto; un rilascio altrove lo lascia aperto (3.0)

Tenuto premuto, il menu si apre e prende il dito (`Modifier.fluidMenuPointerRelay` in testa all'ancora,
`FluidMenuHandOff`): trascinando su una voce e rilasciando la si sceglie. Un rilascio senza essersi
mossi oltre lo slop, fuori dalle voci, su un separatore o su una voce spenta lascia il menu aperto. Un
momento, un colpo: `Open` solo se il menu si apre, `Tick` per voce attraversata, `Tap` alla scelta,
niente al congedo. L'ancora si nasconde nel disegno (`hidden`), mai con l'alpha di un layer sopra il
vetro. Il contenuto sta dentro il vetro formato, mai sopra il vetro che si forma; un tocco fuori, ovunque,
chiude il menu del tasto (anche con `FluidMorphMenuHost` in un palco: fasce dell'host dei modali).

Il perché, una cosa per volta:

- **il dito che ha aperto è quello che sceglie.** Su iOS si tiene premuto, si trascina e si rilascia
  su una voce, ed è il gesto che chi usa il telefono ha in mano da anni; un menu che obbliga a
  sollevare il dito e a toccare di nuovo è un menu a due gesti. Ma chi tiene premuto per *aprire* e poi
  solleva il dito non ha scelto niente: un rilascio che non si è mosso oltre lo slop, che cade fuori
  dalle voci, su un separatore o su una voce spenta, lascia il menu aperto e si sceglie con un tocco.
  Una volta superato lo slop il dito è «armato» e resta tale anche se torna dov'era: ha deciso di
  scegliere. Un rilascio veloce, lontano dall'ultimo movimento, conta come mosso.
- **il relè sta in testa all'ancora e consuma nell'Initial.** I tasti tengono il dito ma leggono nel
  Main, dopo l'Initial dei genitori: senza un consumo già nell'Initial il tasto nascosto continuava a seguire il
  dito sul menu e a suonare la sua grana sotto i tick delle voci. Il relè legge tutto *ignorando il
  consumato* (un discendente che ha già preso il gesto — il pulsante, `clickable` — non deve privare il
  menu del dito), consuma i cambi di quel dito solo dal momento dell'hand-off e consuma anche il
  sollevamento che lo chiude, o `clickable` lo leggerebbe come un click sull'ancora. Un dito che sparisce
  dall'evento senza un sollevamento è un annullo: il menu resta com'è e nessuno sceglie.
- **un pannello, un viaggio, un materiale.** Il menu che si trasforma, il contestuale, le azioni della
  barra e `FluidGlassMenuButton` condividono `FluidGlassMenuPanel` (righe ad altezza fissa che segue il
  font, separatori disegnati, **una** goccia), `FluidMenuJourney` (parte dal tasto *premuto*, cioè dalla
  sagoma che il dito sta gonfiando, e non dal tasto a riposo; il rimbalzo è un gonfiore uniforme attorno
  al centro del pannello; un congedo a metà apertura torna indietro dal punto in cui si trova, con la sua
  velocità) e `panelOptics(compact)`. Tre menu con tre storie erano tre prodotti, ognuno coi suoi difetti.
- **l'apertura è sincrona, e il tasto cede il posto, non sfuma.** Azioni, ancora, sequenza e viaggio
  nello stesso istante del gesto; la superficie compare sulla sagoma del tasto nel fotogramma in cui il
  tasto smette di disegnarsi (`drawWithContent { if (!anchor.hidden) drawContent() }`: è un cambio di
  proprietario, non un'uscita). Prima l'apertura passava da un `LaunchedEffect` e il tasto sfumava con una
  molla sua, e per un fotogramma non c'era né il tasto né il menu. È la regola «niente alpha di un layer
  sopra il vetro» applicata alla staffetta: un vetro che sfuma sopra un altro vetro non è né l'uno né
  l'altro.
- **nessun layer scalato sul vetro.** Il menu contestuale cresce accanto alla riga *per geometria*, da
  un seme (se stesso a 0,42 attorno al perno più vicino a ciò che si è toccato), e la riga sollevata è
  una superficie Fluid-physics sorella dell'immagine registrata: scala solo l'immagine, che non è vetro.
  È la prima delle tre trappole del vetro in movimento — il fondale che si muove dentro la card — e il
  pannello classico non la evitava.
- **la goccia è la goccia.** Una per pannello, la ricetta di `glassDrop` coi numeri del pomello dello
  slider (`GlassDropRecipe`, `glassDropSquash`): nasce sotto il dito e non ci scivola da un'altra voce,
  segue la voce attraversata, e al rilascio si posa bianca per un attimo prima di andarsene — è la
  conferma della scelta. Campiona la pagina con dentro il materiale del pannello (vetro su vetro), e ha
  tutte le sue trasformazioni — posto, scala del viaggio, schiacciamento — nel proprio `layerBlock`,
  mai dentro il layer delle voci.
- **`FluidAlert` sta nell'host.** Vetro vero, l'arrivo che si posa, i pulsanti con la goccia e la stessa
  aptica (`Tick` per pulsante, `Tap` alla scelta); e l'uscita che si vede anche dopo che il chiamante ha
  tolto l'avviso dalla composizione, perché togliere un avviso non è un fotogramma: la voce si ritira
  (`retire`) e la toglie il layer a uscita finita (`finishExit`). `inHost = false` per il `Dialog` di
  sempre, per un avviso che deve stare sopra le finestre di piattaforma.
- **un tasto il cui unico mestiere è il menu lo apre al tocco** (`FluidMenuOpensOn.Tap`: «Ordina», i tre
  puntini) e suona `Open`, non `Tap`; ogni altro lo apre tenuto premuto e il tocco resta la sua azione.
  Il tenuto premuto apre in tutti e due i casi, perché è lui che porta il dito sul menu.

Provato da `FluidMenuSelectionTest` (il dito che decide, lo slop, l'armo, la voce spenta), `FluidMenuRelayTest`
(il consumo nell'Initial, il sollevamento, l'annullo), `FluidMenuPanelTest` e `FluidMenuJourneyTest`
(le celle, il seme, il tasto premuto, il viaggio e l'inversione) e da `FluidMenuHandOffTest`, che li
mette insieme sotto un dito finto in un albero Compose vero con l'aptica registrata (`Open` `Tick` `Tap`,
nessuna grana, il rilascio fermo che lascia aperto). Quello che nessun test dice è com'è il vetro: gli
host dei test non hanno fondale, e la goccia sul vetro, il cambio di proprietario fra tasto e finestra e
la tastiera sopra un avviso con un campo si guardano in mano. `opensOn` (su `FluidBarAction`) ha solo la demo; `opensOnTap` e' coperto da tre test in `FluidApiAdditionsTest`.

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

## A riposo una superficie fisica è una lastra (3.0; il materiale nuovo dalla 4.0)

**Col materiale nuovo (4.0)** una superficie di Fluid-physics al livello `Full`, da Android 13, è disegnata dal
corpo del materiale `Liquid`, ferma e in viaggio: la sua ricetta è **quella di `glassSurface`** per lo stesso ruolo, la
stessa ottica, la stessa taglia (il lato corto della sagoma), lo stesso tema e lo stesso stato (`physicsLiquidRecipe`
chiama `glassRecipe`), più il materiale di viaggio pesato da quanto la sagoma si muove (zero esatto da ferma). Filo,
riflesso e ombra stanno **nel corpo**, sulla sagoma di ogni fotogramma, anche da ferma: così la superficie ferma e il
primo fotogramma del suo viaggio sono lo stesso programma con gli stessi uniform, e non c'è niente che possa scattare
alla partenza o all'arrivo. I pezzi di un gruppo, **a riposo, sono staccati del tutto**; il collo esiste solo in
viaggio. Un gruppo arriva a **otto** pezzi.

**Come si verifica.** Per chi tocca l'engine: `GlassRecipeParityTest` (ferma = `glassSurface` della stessa forma; il
primo e l'ultimo fotogramma di un viaggio = la superficie ferma; la tabella di verità di chi prende il materiale nuovo)
e `PhysicsLiquidTest` (otto pezzi, collo solo in viaggio, margine fermo). Per un'app: niente da fare, se le sue
superfici che si trasformano passano da `fluidPhysicsSurface` (o dai componenti dell'engine); a schermo, nella pagina
«Parità» della galleria, la colonna di Fluid-physics è la colonna di `glassSurface`. `grep -n "tier = FluidPhysicsTier"`
trova le superfici che hanno chiesto il vetro di prima. **Se la violi** (una superficie che si trasforma disegnata
con una ricetta sua, o un tasto fermo di `glassSurface` sostituito in viaggio da un vetro diverso): uno scalino di
velo, filo o ombra al primo e all'ultimo fotogramma di ogni viaggio: il «cambio di materiale» a scatto che prima della
4.0 si misurava sul tablet a ogni menu e a ogni cambio di pagina.

**Col vetro di prima** (3.0) sfocatura, lente, cupola, frangia e pressione escono da `physicsLensUniforms`, gli stessi
numeri della `glassSurface` di prima; bordo e ombra sono registrati con la ricetta del renderer. Prima la superficie fisica non
aveva ombra e il suo bordo era un tratto netto mezzo fuori dalla sagoma: era il motivo per cui non sembrava
vetro come la pillola. In viaggio gli stessi numeri seguono la sagoma: niente scalino alla partenza né
all'arrivo. Il materiale che cambia (`opticsFrom`) si sfuma con `lerpGlassOptics`, e il margine della cattura
resta zero per costruzione (sotto un quarto di pixel è zero: l'arrotondamento non deve rifare la cattura).
L'oltrepasso è un gonfiore attorno al centro del traguardo, mai il lerp oltre l'1; **in viaggio la sagoma si
deforma, ma solo per il moto**: ogni punto del contorno insegue il suo punto a riposo con una molla dura accanto all'ancora e morbida lontano: la parte lontana resta indietro e la coda attaccata all'ancora fa da collo. Tutto vale zero esatto a riposo e con le animazioni ridotte, e a nessuno dei due capi del
viaggio la sagoma differisce dall'ancora o dalla lastra. La molla parte dalla velocità del momento: niente
rincorse (la rincorsa della casa è il preset `FluidPhysicsJourney.House`).

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

## La lambda di `record` dipende solo dal proprio layer (4.0)

Registrare un `GraphicsLayer` una volta e poi ridisegnarlo è il modo in cui l'engine non paga a ogni
fotogramma ciò che non cambia: l'ombra e il bordo di una superficie fisica ferma, l'anello di ogni
`glassSurface`. Ma **registrato una volta non vuol dire eseguito una volta.** Compose tiene la lambda
passata a `record` e la riesegue da sé, anche molto dopo, quando la piattaforma ha buttato la display
list del layer (`GraphicsLayer.recreateDisplayListIfNeeded`). HWUI la butta a ogni layer rimasto anche un
solo fotogramma senza essere disegnato, e capita di continuo: il posto dell'ombra che a riposo non si
mostra, il bordo a riposo che durante un viaggio non si disegna, l'app che torna dallo sfondo.

Per questo la lambda legge **solo ciò che appartiene al suo layer**: valori catturati al momento della
registrazione, o un `Path` e un `Paint` che sono del layer e che nessun altro riscrive. Mai un `Path`, un
`Paint` o uno shader condivisi e riscritti da altri: rieseguita più tardi, la lambda disegnerebbe ciò che
contengono in quel momento, cioè la forma o i raggi di qualcun altro. Lo shader del riflesso è un'istanza
sola per processo, i cui uniform li scrive ogni superficie che disegna un riflesso: si configura **dentro**
la lambda, mai prima di `record` in un pennello tenuto pronto.

È così che è nato il contorno fantasma della 3.x: i due posti dell'ombra di Fluid-physics registravano da
un `Path` comune, e dopo un'andata e ritorno (Cerchio → Quadrato → Cerchio) la lambda del Cerchio,
rieseguita, disegnava il Quadrato. Sotto la forma restava il contorno di quella di prima e la forma perdeva
la propria ombra, dalla 3.0.0 fino alla correzione. La ricerca, con la sequenza e gli altri punti
dell'engine che seguono lo stesso schema, è in `docs/liquid-glass/ricerca/contorno-fantasma.md`.

Un test sul JVM non lo vede da solo, perché lì nessuno butta display list: `PhysicsShadowSlotsTest` e
`HighlightRingReplayTest` lo provano con `GraphicsLayer` veri sopra un doppio della piattaforma che
riesegue la lambda a ogni disegno. Un layer nuovo registrato una volta sola chiede lo stesso test.

Il materiale nuovo della 4.0 la rispetta e ha il suo test: l'ombra a due maschere registra dalla sagoma
che appartiene al suo layer, e `GlassShadowTest` la riesegue come fa la piattaforma (un'ombra rieseguita
ridisegna la propria sagoma, non l'ultima registrata; dopo che la display list è stata buttata torna una
volta sola, e giusta). La luce del materiale nuovo non si registra affatto: si disegna a ogni fotogramma,
e i suoi uniform, che sono di un'istanza di shader condivisa dal processo, si impostano dentro il disegno,
subito prima di disegnare (`drawGlassLight`).

## Il materiale nuovo: il vetro di prima non si tocca, e la scelta sta in un posto (4.0)

Dalla 4.0 il vetro ha due materiali, `Liquid` (il nuovo, da Android 13) e `Classic` (quello della 3.x),
dietro la stessa firma di `glassSurface` (la scheda [componenti/vetro.md](componenti/vetro.md), «Due
materiali»). Due regole li tengono separati.

**Il vetro di prima non si tocca.** È il ripiego di tutto ciò che il materiale nuovo non può disegnare:
sotto Android 13, su un compilatore che rifiuta, sulla fascia in alto, sullo scrim dei modali,
sull'orologio, e dove un'app lo chiede. Per questo il suo codice è rimasto dov'era, dietro l'`if` di
`glassSurface`, e deve restare identico al pixel. Il motivo per essere severi è che un cambio lì non lo
vede nessuno: le sessioni di prova si fanno su dispositivi recenti, che disegnano l'altro materiale, e un
ritocco al ripiego arriverebbe in silenzio a ogni dispositivo sotto Android 13. Un'ottica o una pellicola
nuova per il materiale nuovo va nel suo profilo, mai in `GlassDefaults`, che resta la verità del vetro di
prima.

**La scelta sta in un posto.** Quale materiale disegna una superficie lo decide `resolveFluidGlassModel`,
pura, con le sue domande in ordine; chi disegna vetro la chiede (oggi `glassSurface`,
`glassControlSurface`, l'ombra staccata, la pillola della tab bar e la goccia; Fluid-physics la chiede con
`resolvePhysicsGlass`, che aggiunge il livello; il vetro dipinto di fogli e avvisi la chiede con
`paintedGlassTakesLiquid`, che è `resolveFluidGlassModel` senza forma da misurare), mai per conto suo. Così una
superficie non è mai un ibrido: con `Classic`, sotto Android 13 o con le sorgenti rifiutate, corpo, bordo, ombra e
scrim tornano tutti quelli di prima. Il compilatore si chiede per ultimo, perché
è l'unica domanda che compila, e una superficie che resta `Classic` per un'altra ragione non deve far
compilare programmi che non disegnerà. Se il corpo o la luce della lastra sono rifiutati la superficie
resta `Classic` per intero: un corpo nuovo con il bordo di prima non esiste. Una ragione nuova per restare
al vetro di prima si aggiunge lì e, con lo stesso ordine, ai motivi della diagnostica
(`FluidGlassModelReason`), perché un ripiego che non si legge da fuori è il difetto di `flat` un'altra volta.

Provato da `GlassModelSelectionTest` (la tabella di verità riga per riga: Android 13, l'antenato che chiede
`Classic`, il compilatore che rifiuta, la fascia, le forme, il velo senza spessore, il compilatore chiesto
per ultimo e solo quando decide, corpo e luce insieme, ogni ottica di serie una lastra e lo scrim no), da
`GlassWarmUpTest` (i motivi della diagnostica danno la stessa risposta della risoluzione, su tutta la
tabella), da `GlassDefaultsTest` (le ottiche e le pellicole di `GlassDefaults` sono quelle della 3.1.3), dai
test del vetro di prima rimasti verdi senza una riga cambiata (`GlassMaterialStateTest`, `GlassSizeRuleTest`,
`GlassPanelMaterialTest`, `GlassRimRecipeTest`), e nella galleria da `GlassPathSelectionTest` (il percorso
a ogni livello di API, anche simulato) e `GlassNodeCountTest` (una lastra `Classic` ha ancora 5 nodi e 5
layer, ma solo 2 li legge la diagnostica sul dispositivo: gli altri 3 stanno nei nodi di Kyant e si deducono dal codice). Sul banco lo tiene `glassbench.py compare` sulle immagini `golden/classic`, che dopo l'innesto
sono rimaste identiche. Quello che nessun test dice è com'è il ripiego su un dispositivo vecchio vero: il
livello simulato fa disegnare all'engine i suoi rami sul driver di un dispositivo nuovo, non prova la
piattaforma.

## Il materiale nuovo: i numeri di gusto stanno nel profilo, e le scene escono dalla ricetta (4.0)

**I numeri di gusto vivono solo nel profilo** (`GlassLiquidProfile`): lo spessore e il colore del filo, la
forza del riflesso, il velo, l'ombra, la smerigliatura, la lente, quanto crescono i grandi. Gli shader
dicono *come* si disegna un vetro e non portano numeri di gusto (le costanti che restano lì danno forma alle
curve, e non sono manopole); la ricetta, `glassRecipe`, dice *come* un profilo diventa una lastra di una
certa taglia, e non ne porta nessuno. Prima i valori di serie stavano in coda ai tre file degli shader, e
chi voleva cambiare «il bordo» doveva sapere che metà stava nella luce e metà nel corpo. Ogni numero porta
accanto la sua fonte: la scheda di misura di R4 o R5 con la fiducia, o «a occhio» con la data (e una scelta
di gusto fatta sulle tavole prende il posto della data). Le varianti che vanno in tavola (iOS 27, iOS 26,
«oggi») sono numeri nello stesso file, non altri shader: una tavola è il profilo di serie con un pezzo
cambiato.

**Una scena del materiale nuovo non porta numeri ricopiati a mano.** Uno uniform ricopiato descrive
un'intenzione, non ciò che l'engine disegnerà, e ciò che si misura contro Apple e ciò che si sceglie su una
tavola deve essere ciò che arriva allo schermo. Le scene del banco (`tools/glassbench/scenes/f1a/ricetta/` e
`gusto/`) le scrive un test dalla ricetta, e da quelle nascono le misure, le tavole e le immagini
`golden/liquid`; un altro test confronta, uniform per uniform, ciò che `glassSurface` passa agli shader con
ciò che le scene scrivono, nei due spazi (la cattura rimpicciolita per il corpo, lo schermo per la luce).
Fanno eccezione le prove scritte a mano prima che la ricetta esistesse (`scenes/f1a/prova-*`), che non
diventano mai immagini di riferimento.

La ricetta è pura e riscrive il contenitore che riceve: si chiama a ogni fotogramma di una pressione senza
allocare, e l'ombra tiene la sua specifica finché i numeri non cambiano.

Provato da `GlassDrawParityTest` (tasto, capsula, tab bar, menu e pannello, chiaro e scuro, due densità:
ogni uniform dell'innesto è quello della scena; una pellicola dipinta arriva al corpo come la scena la
scrive; l'adattatore della superficie non aggiunge niente), da `GlassSceneExportTest` (ogni scena imposta
esattamente gli uniform che i suoi programmi dichiarano; ogni variante di gusto cambia ciò che dice e solo
quello), da `GlassRecipeTest` (le varianti danno ciò che dicono; `GlassShadowDefaults` rimanda agli stessi
oggetti del profilo, non a una seconda copia dei numeri; la ricetta riscrive il contenitore che riceve) e da
`GlassModelSelectionTest` (i numeri dell'ombra restano finché non cambiano). Quello che i test **non**
trovano: un numero di gusto scritto in uno shader o nella ricetta, un JSON di `ricetta/` corretto a mano,
una scena non rigenerata dopo un cambio del profilo (si rigenerano con `GLASS_SCENE_DIR`, istruzioni in
`tools/glassbench/README.md`). Quelli li trova la review.

## Gli shader del vetro: ogni sorgente si registra, passa dal lint e, se è nuova, si scalda (4.0)

Dalla 3.0.0 alla 3.1.2 una variabile si chiamava `flat`, parola riservata di SkSL: il compilatore rifiutava
due sorgenti di Fluid-physics, la sonda lo diceva con una sola riga d'avviso, e tutto il motore è rimasto
al livello `Balanced` su ogni dispositivo senza che nessuno se ne accorgesse. Da lì, quattro regole per
ogni sorgente AGSL dell'engine:

- **ha un nome ed è nell'elenco della sonda.** Una sorgente completa (con un `main`) è una costante
  `…ShaderString` registrata in `GlassShaderSources`: la sonda la prova, la diagnostica ne dà il verdetto,
  il banco e il test strumentato la compilano. Un elenco scritto a mano dimentica una sorgente nuova, ed è
  così che una sorgente sfugge a ogni controllo: per questo il test legge i sorgenti Kotlin e fallisce se
  una stringa con un `main` non è nell'elenco, o se il testo dell'elenco non è quello del file;
- **passa dal lint**, che ferma le parole riservate di SkSL usate come nome (l'unione dei lexer di Android
  13–16, `flat` compreso), i cicli che il compilatore non sa srotolare, gli indici che non sono costanti e
  ciò che AGSL non ha. E prima di consegnare `glassbench.py check` la compila sul PC;
- **chi la usa chiede il verdetto e ha un ripiego.** Su un rifiuto si disegna il ramo che c'è sotto
  Android 13, mai un'eccezione nel disegno: la lente, i riflessi e il bagliore chiedono
  `GlassFieldHealth.usable`, il materiale nuovo passa dalla scelta del modello;
- **se è del materiale nuovo, sta in una famiglia del riscaldamento** (una sola), e quelle del vetro di
  prima in nessuna. Un programma nuovo senza famiglia pagherebbe la propria compilazione nel primo
  fotogramma in cui compare, che è ciò che il riscaldamento esiste per evitare.

Gli shader del materiale nuovo vivono in `ui/glass/field/`, e dalla 4.0 disegnano anche Fluid-physics col
materiale nuovo (il corpo a otto pezzi `GlassBody8ShaderString` e il corpo ad anello `GlassBodyRingShaderString`).
La famiglia SDF di Fluid-physics (`ui/fluidphysics/FluidPhysicsShaders.kt`) resta la deroga dichiarata in «Il
morphing vive in un posto solo», ma solo per il vetro di prima. In tutto le sorgenti registrate sono **14**: nove
del vetro di prima e di Fluid-physics, cinque del materiale nuovo (`Body1`, `Body8`, `BodyRing`, `Light1`,
`Light8`). Le famiglie del riscaldamento sono due: `Slab` (corpo e luce a un pezzo, due passi) e `Group` (corpo a
otto pezzi, luce a otto pezzi, corpo ad anello: tre passi), che chiede una superficie di Fluid-physics col materiale
nuovo quando entra in composizione. Oggi nessuno disegna la luce a otto pezzi (`Light8`): Fluid-physics compone la
luce nel corpo, e la famiglia la scalda lo stesso.

**Come si verifica.** `ShaderSourceLintTest` (per chi tocca l'engine); per un'app `adb logcat -s FluidGlass
FluidPhysics` su un dispositivo vero: nessuna riga d'errore vuol dire nessuna sorgente rifiutata, e
`FluidGlassDiagnostics.shaderVerdicts` lo dice dall'app. **Se la violi** (una sorgente nuova fuori dall'elenco, o
fuori da una famiglia): un rifiuto del compilatore che nessuno vede, come il `flat` della 3.x, o uno scatto da
20–40 ms al primo fotogramma in cui il programma compare.

Provato da `ShaderSourceLintTest` (ogni sorgente passa il lint; ogni stringa con un `main` è nota alla
sonda col suo testo; ogni sorgente registrata è del vetro di prima o è scaldata da esattamente una
famiglia; il lint ferma il `flat` della 3.1.2, le parole riservate, i cicli e gli indici che non vanno),
da `GlassShaderFallbackTest` (lente, riflessi e bagliore rifiutati prendono il ramo di sotto senza
eccezioni; nessun percorso di disegno arriva al compilatore senza un verdetto; il verdetto non si chiede a
ogni disegno), da `GlassWarmUpTest` (ogni passo di una famiglia compila un programma diverso della sua
famiglia) e sul dispositivo da `GlassShaderCompileInstrumentedTest`, che compila ogni sorgente col
compilatore vero e prova che rifiuta ancora `flat`.

## Il materiale nuovo: una tinta a cui l'app ha dato un colore resta dipinta (4.0)

`GlassTint` ha un modo (`GlassTintMode`) che dice al materiale nuovo cosa fare di `overlay`: `Fixed` la
dipinge com'è al posto del velo, `Neutral` lascia il velo al materiale, `Colored` la posa sopra il velo.
Le tinte di famiglia di `GlassDefaults` sono `Neutral`, perché nel materiale nuovo il velo è del profilo.

Ma un'app che dà un tono a una lastra lo fa con `GlassDefaults.barTint().copy(overlay = colore)`, e se il
modo sopravvivesse alla `copy` il materiale nuovo quel colore non lo dipingerebbe: l'app cambierebbe
aspetto senza aver toccato una riga, che è proprio ciò che una versione non deve fare. Quindi **`Neutral`
vale solo per la pellicola con cui la tinta è nata**: se `overlay` non è più quella, la tinta si legge
`Fixed`. Conta solo il colore, non l'ordine delle chiamate né chi le fa; `fallback` e `hairline` non
toccano il modo. E **sopra il contrasto standard ogni tinta è la pellicola densa** (`forContrast` la mette
in `Fixed`), perché chi chiede più contrasto non vuole un velo che lascia passare lo sfondo.

Per chi scrive una tinta nuova nell'engine: dice il suo modo (`Neutral` per una pellicola di famiglia,
`Fixed` per una pellicola che è tale per mestiere, `Colored` per un colore d'origine) e passa da
`forSystemContrast()` una volta sola, come le altre.

Provato da `GlassTintModeTest` (la tinta scritta a mano dipinta com'è, quella di serie col colore cambiato
che lo mostra, l'ordine delle chiamate che non conta, il colore e non la storia, `fallback` e `hairline` che
non toccano il modo, il contrasto alzato che dà la pellicola densa, il costruttore a tre argomenti di
sempre), da `GlassDefaultsTest` (la famiglia è il velo e le pellicole sono dipinte; una tinta di serie col
colore dell'app lo mostra; col contrasto alzato ogni tinta di serie è la pellicola densa, una volta sola),
da `GlassCompatSignaturesTest` e `GlassModelSelectionTest` (una tinta a tre argomenti resta la pellicola
dipinta) e da `GlassDrawParityTest` (la pellicola dipinta arriva al corpo).

## Il materiale nuovo: il margine della cattura resta fermo, e il filo non cresce con la taglia (4.0)

**Il margine della cattura non si muove.** Il raggio di sfocatura decide il margine che la cattura
registra attorno al pannello, e un margine che cambia rifà la cattura a ogni fotogramma (è l'invariante
della regola della taglia, «La taglia cambia il conto»). Nel materiale nuovo la comparsa e `intensity`
non toccano il raggio: passano dalla mescola fra nitido e smerigliato e dalla comparsa nello shader. La
qualità adattiva scala il raggio, come ha sempre fatto, ma il margine si calcola una volta, a qualità piena,
e resta quello; ed è un numero intero di pixel, perché il layer si posa al margine e una frazione
sposterebbe il vetro rispetto al nodo.

**Il filo non cresce con la taglia; smerigliatura e ombra sì.** Lo spessore del filo e il rientro del
riflesso sono assoluti, gli stessi su un tasto, una capsula, un menu e un pannello: è così nelle misure di
iOS (R4 A1), e un filo che cresce con la lastra diventa una fascia, il difetto che la tavola del bordo
nomina. La smerigliatura e l'ombra crescono con la regola della taglia dell'engine, perché un pannello
grande smerigliato come uno piccolo sembra un piccolo ingrandito.

Provato da `GlassRecipeTest` (`intensity` scala il materiale senza toccare il raggio; a intensità zero lo
sfondo è nitido; la qualità scala forma e raggio e mai il velo; il margine copre la sfocatura che la lente
non copre; il filo non cresce con la lastra; smerigliatura e ombra crescono col guadagno della taglia), da
`GlassModelSelectionTest` (il margine è di pixel interi e non si muove con intensità, qualità o pressione)
e da `GlassSceneExportTest` (nelle scene il filo è lo stesso su ogni lastra, e i grandi sono più
smerigliati).

## Un materiale solo: ogni strada del vetro passa dalla ricetta, e chi deriva non ha numeri suoi (4.0)

Il materiale nuovo arriva allo schermo da cinque strade: `glassSurface` (e `glassControlSurface`), le superfici di
Fluid-physics ferme e in viaggio, la pillola della tab bar, la goccia dei controlli e dei menu, il vetro dipinto di
fogli e avvisi in un'altra finestra. **Tutte passano dalla ricetta** (`glassRecipe`) con il profilo di serie, o con
un profilo **derivato** da quello di serie con `copy` (`GlassPuckProfile.kt`, `GlassDropProfile.kt`,
`GlassPaintedProfile.kt`). Un derivato dice soltanto ciò che lo distingue, e lo dichiara: la pillola quanto è più
scura della barra (la selezione), la goccia il tono al posto del velo, quanto piega e che è piatta, il dipinto lo
scrim, la pagina di riferimento, la pellicola della selezione e i passi del disegno. Tutto il resto (velo, filo,
riflesso, ombra, smerigliatura, lente) lo prende dal profilo che riceve.

Il perché: prima di F1B ognuna di queste strade aveva la sua ricetta, e messe in fila (un tasto, il pomello, la
pillola, un menu, un foglio) erano cinque vetri. Una ricetta ricopiata diverge al primo ritocco del profilo, e
nessun test della singola strada lo vede.

**Come si verifica.** `GlassRecipeParityTest`: una superficie fisica ferma riceve gli stessi numeri di corpo, luce e
ombra della `glassSurface` della stessa forma; cambiando velo, filo, riflesso, ombra, smerigliatura o lente in una
copia del profilo di serie, la pillola, la goccia e il dipinto cambiano con lui; i numeri scritti nei tre file dei
derivati sono un elenco chiuso (un numero nuovo non dichiarato fa fallire il test); nessun derivato nomina una
variante fissa del profilo (`GlassLiquidProfile.Shipped`, `GlassLiquidEdges.…`, `GlassLiquidVeils.…`). A schermo: la
pagina «Parità» della galleria (`--es gallery.page parity`) mette le strade una accanto all'altra sullo stesso fondo.
**Se la violi.** Il vetro torna a essere tanti vetri: un pomello o una pillola con un filo diverso da quello dei
tasti, un foglio dipinto che non cambia quando cambia il profilo, e una scelta di gusto fatta sulle tavole che
arriva a quattro strade su cinque.

Il test prova anche la tabella di verità delle cinque strade (modello chiesto × livello di API × livello di
Fluid-physics): nessuna prende il materiale nuovo sotto Android 13 o con `Classic`, nessuna lo lascia dove dovrebbe
averlo. Non ha prove saltate: le due disparità che aveva trovato (il dipinto che non chiedeva il modello, un passo di
velo al primo fotogramma di un viaggio sul tema scuro) sono state corrette, e ora le prova.

## Il vetro dipinto e lo scrim: uno scrim solo, e il dipinto è la ricetta (4.0)

Un foglio (`FluidSheet`) o un avviso (`FluidAlert`) fuori da un `FluidGlassModalHost` stanno in un'altra finestra
di piattaforma, dove non c'è niente da catturare: il loro vetro è **dipinto** (`fluidStaticGlassSurface` e
`glassPainted`). Dalla 4.0 il dipinto non ha numeri suoi: corpo, filo sul perimetro, riga chiara e alone dentro il
bordo, ombreggiatura verso il contorno e ombra si calcolano dalla ricetta di serie con le formule degli shader. Il
corpo è il colore **pieno** che il vetro vero avrebbe sopra la pagina del tema sotto lo scrim: su una pagina
uniforme è esatto, su una foto o un testo no, ed è la differenza dichiarata (un dipinto non finge una pagina sfocata).

**Lo scrim è uno**: un nero al **12%** sul chiaro e al **20%** sullo scuro, per i modali di vetro dell'host e per i
pannelli dipinti. I livelli stanno in `GlassPaintedScrim`, e chi disegna uno scrim li legge da lì (anche
`FluidGlassModal`). Prima il codice chiedeva tre scrim per la stessa cosa: un nero al 42% per il foglio di
piattaforma, il velo della `Dialog` per l'avviso, il 12/20% per i modali dell'host. Quale dei due casi tocca a un'app
lo decide l'host: **dentro un `FluidGlassModalHost`**, che è il caso normale di un'app dell'engine, `FluidSheet` e
`FluidAlert` sono modali di vetro dell'host e il loro scrim era e resta il 12/20%; **fuori da un host** (un'app senza
host, o un avviso con `inHost = false`) sono il foglio di piattaforma (`ModalBottomSheet`) e la `Dialog`, che col
materiale nuovo passano dal 42% e dal velo della `Dialog` allo scrim unico, e con `Classic` o sotto Android 13 li
riprendono. Lo scrim dell'host in più smeriglia la pagina; quello di piattaforma non può, e non lo compensa scurendo
di più. Un vetro dipinto **sulla pagina**, fuori da un foglio o da un avviso dipinti, non ha scrim sotto e si calcola
sopra la pagina del tema.

**Il dipinto sceglie il materiale come ogni altro vetro** (`paintedGlassTakesLiquid`, chiesto da
`fluidStaticGlassSurface`, `FluidSheet` e `FluidAlert`): da Android 13, col materiale `Liquid` e le sorgenti delle
lastre accettate, è quello descritto qui; con `FluidGlassModel.Classic`, sotto Android 13 o con le sorgenti
rifiutate è **per intero** quello della 3.x (il dipinto di prima, il contenitore al 92%, lo scrim e il velo della
`Dialog` di prima, nessuna ombra del vetro). Mai un foglio nuovo con l'ombra di prima, né il contrario.

**Come si verifica.** Nel codice di un'app: `grep -n "Color.Black.copy(alpha" ` attorno a un foglio o a un dialogo:
nessuno scrim scritto a mano sopra quelli dell'engine; `grep -n "scrimColor\|setDimAmount"`: nessun valore proprio. A
schermo: un foglio di piattaforma e un foglio dell'host abbassano la pagina dello stesso tanto; con `Classic` il
foglio di piattaforma è tutto quello della 3.x (pagina dietro leggibile attraverso il contenitore al 92%, nessun filo
del vetro nuovo). Il foglio di piattaforma si vede solo **senza host**: nella galleria è la pagina «Vetro dipinto»
(`--es gallery.page painted`); le altre pagine hanno l'host alla radice e mostrano il foglio di vetro. Per l'engine: `GlassPaintedProfileTest` (lo scrim è uno, il corpo è la formula dello shader, il
dipinto segue il profilo) e `GlassRecipeParityTest` (la riga del dipinto nella tabella di verità). **Se la violi.**
Due fogli della stessa app con due pagine dietro di luminosità diversa, un foglio dipinto che resta indietro quando il
profilo cambia, o un foglio ibrido (corpo di un vetro, ombra o scrim dell'altro).

## Il passaggio di consegne fra il vetro che viaggia e il vetro fermo: una decisione sola, letta nella stessa fase (4.0)

Quando una forma ferma comincia a viaggiare, o un viaggio finisce, il vetro passa da una superficie all'altra: i
tasti della barra ancorata, ognuno col suo `glassSurface`, lasciano il posto alla superficie di Fluid-physics che li
porta fra le due pagine, e alla fine la riprendono. **Il passaggio lo decide una decisione sola**, e tutti e due i
chiamanti la leggono **nella stessa fase**: oggi in composizione. La superficie che viaggia legge `journey.active` nella
composizione della barra; i tasti leggono la stessa decisione nella stessa passata di composizione
(`glassControlSurface`, attraverso `LocalFluidBarPieceSink`, e in composizione montano o tolgono il proprio vetro).
Chi poi disegna (la superficie che viaggia) usa **il valore di quella composizione**, passato come valore, e non
rilegge lo stato vivo nel proprio disegno. Il passaggio cade **quando la forma è ferma**, non quando la pagina dietro
finisce di posarsi. In ogni fotogramma il vetro lo disegna o la superficie che viaggia o il tasto, mai nessuno dei
due e mai tutti e due.

Il perché: uno stato può cambiare **dopo** la composizione (in un `SideEffect`, al posizionamento), e allora una lettura
in disegno lo vede nel fotogramma stesso, mentre una lettura in composizione lo vede solo al fotogramma dopo. Se dei
due chiamanti uno legge in composizione e l'altro in disegno, per un fotogramma non sono d'accordo: è esattamente ciò
che succedeva fino alla 4.0, con due segnali diversi (la pagina che si posa e uno stato scritto in un `SideEffect`).
Sul Tab S9 ogni arrivo aveva **un fotogramma senza vetro** (6 arrivi su 6), alla partenza un fotogramma con due vetri e
l'ombra doppia, e il vetro tornava ai tasti quando la molla della pagina scendeva sotto mezzo pixel, molto dopo che la
forma si era fermata. Leggere in composizione da tutte e due le parti è la scelta di oggi perché i tasti, per togliere
il vetro, devono smontare un modificatore, e quello si fa solo in composizione. Col materiale nuovo fermo e viaggio
sono lo stesso vetro, quindi il passaggio non si vede: purché sia uno.

**Come si verifica.** Per l'engine: `FluidBarHandoffTest` (falliva 3 su 3 sul codice di prima). Per chiunque scriva
una consegna simile (una superficie che sostituisce dei vetri fermi durante un moto): trova lo stato che decide **chi**
disegna il vetro e **tutti** i punti che lo leggono (`grep -n` sul suo nome). Devono essere nella stessa fase: tutti
nel corpo di un `@Composable`, o tutti dentro lambda di disegno (`drawBehind`, `drawWithContent`, `onDraw…`,
`graphicsLayer {}`), mai misti; e chi disegna con il valore deciso in composizione lo riceve come valore
(`rememberUpdatedState` del valore letto lì), non rilegge lo stato dentro la lambda. Un `SideEffect` che **scrive**
quello stato è lecito (la decisione arriva a tutti e due alla composizione dopo); non lo è uno dei lettori che la vede
prima dell'altro. A schermo, a fotogrammi (`-fps_mode passthrough`, il rivelatore di scatti
`tools/glassbench/glassjerk.py`): all'arrivo nessun fotogramma con le sole icone, alla partenza nessun fotogramma con
l'ombra doppia. **Se la violi.** Un lampo di icone senza vetro alla fine di ogni push e di ogni ritorno, e un bordo più
scuro (due vetri sovrapposti) nel primo fotogramma del viaggio.

## Un tasto che nasce dal fianco del vicino nasce un dp dentro (4.0)

Un viaggio in cui un tasto **nasce** da quello accanto (un pezzo che da taglia zero cresce attaccato al vicino e se
ne stacca col collo) parte da un **seme posato un dp dentro il fianco del vicino**, non sul suo bordo. Il motore non
disegna pezzi più piccoli di un pixel (`MinSlabSidePx`): un seme posato esattamente sul bordo sporge di quel pixel al
primo fotogramma, e si vede un punto di vetro fuori dal tasto prima che il viaggio cominci.

Oggi questo viaggio lo costruisce solo il banco (scena `f1b/ponte-stretto`, riga «nascita»); è la regola per chi lo
costruirà nell'engine (la barra fra le pagine, dove un tasto nuovo nasce dal vicino).

**Come si verifica.** `F1bExportTest`: al progresso zero il seme sta tutto dentro il vicino, e la resa del primo
fotogramma è quella del vicino da solo. Nel codice: il riquadro di partenza del pezzo che nasce è dentro il riquadro
del vicino (`BirthSeedInsetDp` nella scena del banco). **Se la violi.** Un pixel di vetro che spunta dal fianco del
tasto nel primo fotogramma di ogni nascita.

## La scheda scelta la dicono la pillola e l'accento (4.0)

Da Android 13, col materiale nuovo, la pillola della scheda scelta di `FluidFloatingTabBar` (e quindi di
`FluidTabBar` e `FluidFoldingTabBar`) è **vetro**: il vetro della barra un po' più scuro, sui due temi, con il filo
sottile e l'ombra ridotta di un vetro dentro un vetro (`GlassPuckProfile`). Non è più una macchia d'accento:
**l'accento passa all'icona e all'etichetta** della scheda scelta, portato fino a 3:1 contro il fondo che ha dietro.
`FluidFloatingTabBarGlass.puckColor`, se l'app lo scrive, diventa una **pellicola** di quel colore sulla pillola, non
un riempimento pieno. Sopra il contrasto di sistema standard la pillola segue la pellicola densa della barra
(`glassPuckFilm`), come il resto della barra, e l'etichetta scelta, che è testo, tiene almeno 4,5:1 e mai meno
contrasto dell'inchiostro di prima. Sotto Android 13 e con `FluidGlassModel.Classic` la pillola è quella di prima.

**Come si verifica.** Nel codice: `grep -n "puckColor"`: se c'è, è una scelta (una pellicola tinta), non il modo di
far vedere la scheda scelta; nessuna scheda con l'icona colorata a mano. A schermo: la pillola è grigia e un po' più
scura della barra; l'icona e l'etichetta scelte sono nell'accento. **Se la violi** (un `puckColor` pieno d'accento
scritto per «far vedere» la scheda): la pillola torna una macchia colorata sopra un vetro, e l'icona d'accento sopra
una pillola d'accento non si legge.

## Le app: sotto la barra di navigazione a tre tasti si spegne il velo di contrasto (4.0)

Android, da 10 in su, sotto la barra di navigazione **a tre tasti** stende da sé un velo di contrasto (un nero di
circa il 40% su tutta la larghezza) quando un'app disegna fino al bordo: `window.isNavigationBarContrastEnforced`, vero
di serie, anche con `enableEdgeToEdge()`. Sopra un foglio di vetro, un modale o una tab bar a filo dello schermo è
una **fascia scura in fondo**, che non è dell'app né del vetro (sul Tab S9, tema scuro). Le app dell'engine
disegnano fino al bordo, quindi lo spengono, subito dopo `enableEdgeToEdge()`:

```kotlin
enableEdgeToEdge()
if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
```

Con la navigazione a gesti il velo non c'è e la riga non cambia niente. L'engine oggi non lo fa da solo.

**Come si verifica.** Nel codice: `grep -rn "isNavigationBarContrastEnforced" app/src/main`: c'è, nella `onCreate` di
ogni `Activity` che chiama `enableEdgeToEdge()`. A schermo: con la navigazione a tre tasti (Impostazioni → Sistema →
Navigazione), tema scuro, un foglio aperto: il fondo del foglio arriva al bordo senza banda. **Se la violi.** Una
fascia più scura alta quanto la barra di navigazione in fondo a ogni foglio e a ogni modale, sui dispositivi a tre
tasti.

## Le app: le pagine delle schede restano vive (4.0)

Il cambio di scheda è un viaggio della pillola della tab bar, che dura qualche centinaio di millisecondi. Se a ogni
cambio la pagina della scheda d'arrivo si **compone da zero** (un `when (scheda) { … }` che smonta la pagina di prima e
monta l'altra), il lavoro di quella composizione cade dentro il viaggio, e sul Tab S9 la pillola **si ferma 100–190
ms a metà strada** a ogni cambio (misurato prima della 4.0: 3 su 3 per tema, schede vicine e lontane): l'occhio lo vede nella pillola,
anche se il costo è della pagina. Quindi le pagine delle schede si tengono **vive** (composte anche quando non si
vedono) o si **precompongono** prima del viaggio, e lo stato si conserva (`rememberSaveableStateHolder`).

Un vincolo da sapere: solo la pagina visibile deve registrare il fondo che la barra campiona (una `FluidScreen` lo fa
da sola); due pagine composte che lo registrano insieme fanno sfocare alla barra la pagina sbagliata. La galleria,
per questo, oggi compone solo la scheda in vista, e paga lo stallo.

**Come si verifica.** Nel codice: dentro il contenuto della tab bar, un `when` sulla scheda scelta senza
`SaveableStateHolder`, senza un `Pager` o un contenitore che tenga le pagine composte, è il caso da guardare. A
schermo, a fotogrammi a 120 Hz: nel viaggio della pillola nessun passo da 100 ms e oltre (il rivelatore di scatti
`tools/glassbench/glassjerk.py` lo segnala come `LUNGO`; vedi `tools/glassbench/SCATTI.md`). **Se la violi.** La pillola parte, si ferma per un decimo o due di secondo a metà, e
riparte: il cambio di scheda sembra lento anche su un tablet veloce.
