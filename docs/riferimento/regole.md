<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/regole.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

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

**Una goccia in una lastra ha la faccia del tema** (fase 9). Il bianco fisso è dei pomelli, che stanno su una pista
colorata e di cui è l'identità; il segmentato, il rail e la lente dell'indice stanno in una lastra, e lì la faccia è
`GlassDropFaces.forSurface(isDarkSurface())`: bianca sul chiaro, il grigio di iOS (0x636366) sullo scuro, perché un
bianco pieno su una pista scura è un faro e l'etichetta `onSurface` che ci sta sopra non si leggerebbe. È sempre
`glassDrop`, un modificatore solo con la faccia e il movimento come parametri (una `GlassDragAnimation`, o le lambda di
`GlassDropMotion` per la lente dell'indice, la cui pressione è l'apertura del nastro): prima c'era un secondo
modificatore, `glassDropFace`, e due ricette che dicevano la stessa cosa sono due ricette che divergono. L'alfa della faccia
bianca è `1 − p` bit per bit come nei pomelli (`GlassDropFaceTest`).

**Un ingresso non scala un vetro.** L'indice di sezione, la sua bolla e il suggerimento entrano per offset di layout e
alfa, mai per `scaleIn`: un layer scalato sopra un vetro rifrange ciò che non c'è più (è la prima delle tre trappole in
fondo), e un `AnimatedVisibility` con `scaleIn` attorno a una pista di vetro la fa "respirare" a ogni comparsa.

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

## A riposo una superficie fisica è una lastra (3.0)

Sfocatura, lente, cupola, frangia e pressione escono da `physicsLensUniforms`, gli stessi numeri di
`glassSurface`; bordo e ombra sono registrati con la ricetta del renderer. Prima la superficie fisica non
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
