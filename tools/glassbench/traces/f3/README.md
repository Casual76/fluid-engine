# F3.1: contratto del piano puro

I sei `*/trace.json` sono esportati dai test, chiamando `GlassPlaneJourney` di produzione.
Non sono animazioni riscritte nel banco. Le uscite di verifica stanno in `banco/` (ignorata da git).

## Tipi per F3.2, F3.3 e F4A

- `GlassPlanePiece`: chiave del nodo, contenitore, riquadro in pixel, quattro raggi già risolti,
  variante del materiale, `id` e `unionId` già qualificati dal namespace, transizione, ruolo e simbolo.
- `GlassPlaneRegistry`: `put`, `remove`, `snapshot`. `GlassPlaneState` copia le iscrizioni e calcola
  i grappoli; `clusterOf(key)` restituisce identità strutturale, membri e riquadro del gruppo.
- `GlassPlaneJourney`: preparazione una volta per cambio di stato, `frame(tMs)` per il disegno,
  `retarget(atMs, nuovoStato)` anche per interruzioni successive. `planeJourneyFrame` è la scorciatoia
  pura della tabella 4.3, con un'interruzione facoltativa.
- `GlassPlaneFrame`: lastre in volo, raggi, `materialize`, velocità dei quattro lati e raggi,
  parentela (`bornFrom`, `mergesInto`), riquadro finale del contenuto, campi con palco e kernel fissi.
  La velocità è in pixel al secondo, quella di `materialize` in unità al secondo.

Il cliente conserva il viaggio; non deve ricostruirlo a ogni fotogramma. `finished` riguarda la
geometria del piano, non il completamento della pagina. Una lastra che confluisce in un'altra resta
nel campo, coincidente all'arrivo: il cliente restituisce poi la superficie ai grappoli di riposo.
I pezzi non devono disegnare un secondo vetro mentre il palco li possiede. Il passaggio Compose,
le catture e la sonda uniforme appartengono a F3.2.

## Scelte esplicite

- La distanza è euclidea tra i bordi dei riquadri. La connessione è transitiva e non attraversa
  contenitori o varianti di materiale. Una stessa unione esplicita collega i membri anche oltre
  `spacing`; non oltrepassa il limite di otto.
- Sopra quattro si preferisce dividere solo se esiste un varco positivo che non taglia un'unione.
  Sopra otto si divide comunque nel varco più largo lungo l'asse maggiore del gruppo; a parità si
  preferiscono metà bilanciate. Il limite vale anche per il totale delle lastre in transito.
- I candidati anonimi seguono `id`, ruolo, simbolo, ordine dall'esterno e mantengono il lato del
  contenitore. Tra più vicini vince il varco minore, poi la distanza tra i centri.
- **Conflitto del contratto:** due id espliciti che si scambiano il posto su una riga richiederebbero
  traiettorie incrociate. Il piano li rende localmente per lente e restituisce `crossingIds`.
  Questo è un ripiego dichiarato, da ratificare dall'orchestratore; non è un percorso curvo inventato.
- La spec è `FluidMotion.standard<Float>()`, valutata in forma chiusa dal motore delle animazioni
  Compose, senza composable o clock UI. Cambia solo l'unità di tempo: 300 ms per il contesto
  (`FluidMotion.DurationExpand`), 430 per la comparsa e 470 per la scomparsa (R4 B2/B3).
  Ogni canale conserva la velocità fisica anche dopo il cambio dell'unità di tempo. I canali che
  viaggiano insieme hanno lo stesso orologio. Il palco comprende il limite conservativo della
  risposta della molla, compresa l'inerzia ereditata.
- `GlassPlaneMotion` raccoglie temporaneamente i nuovi bersagli e la scala iniziale 1,09 (centro
  della forchetta R4 B3 8-10%, fiducia media). Consolidarli in `FluidMotion` richiede una modifica
  fuori perimetro. Nessuna scelta di `spacing` di serie è introdotta da F3.1.

## Tracce e confini della prova

Scenari: `uno-due`, `divisione`, `contesto-capsule`, `comparsa`, `uno-due-interrotto`,
`divisione-interrotta`. Cadenza 60 Hz, densità 3, cinque fotogrammi di preambolo e coda a riposo;
le due interruzioni cadono a 150 ms. `_piano` conserva chi nasce da chi, velocità e riquadri in
pixel per F3.3. `_kernel` registra la variante scelta per tutto il viaggio.
`uno-due` esercita la nascita; `divisione` ne esercita il verso inverso, due verso uno, per dare
al banco anche il riassorbimento. Gli stati geometrici iniziali e finali sono fissati nel generatore.

Il materiale usa `glassRecipe`, `physicsLiquidInputs` e lo scrittore fisico esistente. Il collo
resta spento: non si duplica la sua coreografia, che è di F3.3. Non si aggiunge una coreografia
del contenuto (F3.4). Non è quindi una prova ottica di F3-a/c né del passaggio Compose F3-b.
Il README sotto `test/.../trace/` citato nel brief non esiste; il formato è quello di
`tools/glassbench/TRACE.md`.

Per riesportare, nel batch coordinato impostare `GLASS_PLANE_TRACE_DIR=tools/glassbench/traces/f3`.
La variabile distinta evita che gli altri test riesportino tracce fuori dal perimetro F3.1.
Per leggere e rendere usare il Python di `artifacts/liquid-glass/venv`, `glassmotion.py validate`,
`first`, `last` e, per gli eventi, `first --at 150`, con `-o` dentro `banco/<scenario>/`.

## Backlog del dispositivo e integrazione

- S3/F3.2: nessun fotogramma con vetro doppio o mancante nel passaggio riposo-palco; stessa ricetta
  sui due lati della consegna. Padding e margine ottico si aggiungono al palco geometrico dal cliente.
- F3.3/S3: collo e rifrazione dei ponti, comparsa su foto vera e testo, confronto R4 B2/B3.
  Le tracce di F3.1 portano geometria e parentela, non certificano la resa dei ponti.
- S3: costo dei campi a quattro e otto lastre e ricattura mentre il contenuto sotto scorre.
- F3.2/F4A: collegare registro e clock reali, namespace e diagnostica di `crossingIds`; un test JVM
  del piano non prova che il layout o il chiamante usino questi risultati.

## Verifica eseguita e delta successivo

Un solo batch proprio: `build-gradle.ps1 -Task ":engine-ui:testDebugUnitTest" -Name lg-f3-1`,
PASS in 279 secondi: 5.963 test, zero fallimenti/errori, 1.654 saltati preesistenti. Eseguiti e verdi
21 casi di `GlassPlaneTest` e un caso di `GlassPlaneTraceExportTest` che esporta tutti i sei scenari.
Prima di questo batch, il batch parallelo F3.5 aveva rilevato due problemi nei file F3.1 in preparazione:
tolleranza Float dei raggi e differenza finita sotto il passo della spec Compose; entrambi corretti.

**Delta successivo al batch, non eseguito:** nel controllo statico finale è stata aggiunta la guardia
che toglie l'id alla vecchia lastra uscente anche se `Materialize` sostituisce il nodo mantenendo lo
stesso id con una chiave di registrazione diversa. Altrimenti l'interruzione tentava uno snapshot
con due id uguali. Il ventiduesimo caso `interruptedMaterializeOnAReplacementNodeDoesNotDuplicateItsExplicitIdentity`
è preparato ma non eseguito. Non si lancia un secondo batch, in accordo al limite del brief.
La guardia non cambia alcuno dei sei scenari esportati; il prossimo batch d'integrazione deve
includere questo caso. Il verde sopra non viene attribuito a questo delta.

`glassmotion.py validate` legge tutte le sei tracce: 48 fotogrammi per scenario, da -83,333 a 700 ms.
Eseguiti `first`, `last`, `first --at 0` su tutte, e `first --at 150` sulle due interrotte, con un
solo processo di rendering: 288 fotogrammi resi. Guardati i collage iniziali e finali e quelli
delle due interruzioni. Il raccordo dal preambolo a t0 è identico al pixel (differenza media zero)
e gli ultimi cinque fotogrammi sono identici in tutti gli scenari. Nessun salto istantaneo di
posizione osservato all'evento; i test eseguiti confrontano anche la velocità al cambio di viaggio.
La giunzione fra i lobi è ancora l'unione senza collo: è visibile nei collage, non è qualificata.
Non eseguiti `pair` con i filmati Apple o verifiche sul dispositivo; i tempi sono i budget nominali
del piano e non una certificazione percettiva di R4. `banco/verifica.json` conserva controlli
numerici e distinzione fra batch eseguito e delta successivo non eseguito.

## F3.11: la coreografia della nascita e della confluenza

Le sei tracce sono state riesportate dal Kotlin dopo F3.11 (`GLASS_PLANE_TRACE_DIR=tools/glassbench/traces/f3`):
cambiano `uno-due`, `divisione` e le due interrotte; `contesto-capsule` e `comparsa` sono identiche al byte. Le misure
di Apple sono in `artifacts/liquid-glass/sessioni/f3-11/misure.md`. Cosa cambia nel contratto:

- **Chi nasce da un vicino non parte più dal suo riquadro di partenza.** Sta **dentro il vicino in volo** (della sua
  misura, o di quella del vicino se è più piccolo, a filo del bordo dalla parte del suo posto), se ne sporge un poco, e
  dopo il ritardo (`FluidMotion.PlaneReleaseDelay`, 0,17 del bilancio; +0,09 per ogni fratello più vicino dalla stessa
  parte) ne è **rilasciato** con la molla del piano smorzata a 0,54: il lato di testa supera l'arrivo del 13% della
  corsa, quello di coda al più del 4%. Al primo fotogramma coincide col vicino solo se ha la sua misura o è più grande.
- **Chi confluisce in un vicino** ha una molla (0,62) sullo scarto dal vicino **in volo**: gli entra dentro dovunque
  stia andando, e il lato di testa esce dall'altra parte dell'8% della corsa.
- **Chi si sposta soltanto non cambia**: stessa molla, stesso orologio, superamento ≤1,3%.
- **La staffetta** (`GlassPlanePieceFrame.relay`): un vetro che se ne va confluendo nel vetro che nasce da lui vola come
  un vetro solo, con la molla di chi si sposta.
- **Tra più vicini** vince ancora il varco minore; a parità, e per chi confluisce, non più la sola distanza fra i centri
  ma la distanza più due volte la differenza di misura (`planeKinship`): a misure uguali è la regola di prima.
- **Due vetri fusi non si incrociano**: `planeCrosses` non conta più un sorpasso fra due vetri di cui uno copre il centro
  dell'altro (prima solo se i centri erano uguali).
- `settleMs` resta il bilancio (300 ms); la fine del viaggio (`durationMs`) è la fine della coda del rilascio (345 ms).
  Il palco contiene il superamento, ed è ancora fisso per tutto il viaggio.
- Con una spec dell'app (`GlassPlaneMotion.spec`) niente di questo: la spec porta la sua curva, com'era.

## F3.12a: i tempi di Apple a tempo vero, l'attesa, il rinculo, il pezzo che arriva gonfio

Le sei tracce sono state riesportate dal Kotlin dopo F3.12a, e ora hanno **53 fotogrammi** (da −83 a 783 ms: una
nascita ripensata a 150 ms finisce a 650, e servono cinque fotogrammi fermi dopo). `contesto-capsule` e `comparsa` hanno
i primi 48 fotogrammi identici a prima, più cinque fermi: chi si sposta soltanto e chi compare per lente non cambiano.
Le tracce `collo-*` di `F33Traces` sono state riesportate con loro (`GLASS_F33_DIR`): fino a F3.11 erano rimaste alla
geometria di F3.2c. Le misure di Apple sono in `artifacts/liquid-glass/sessioni/f3-12a/misure.md`. Cosa cambia nel
contratto, rispetto alla sezione sopra:

- **Una nascita e una confluenza durano 500 ms** (`FluidMotion.PlaneReleaseMillis`), ed è lì che cadono sia il riposo a
  vista (`settleMs`) sia la fine del viaggio (`durationMs`): contenuto, luce, rigonfiamento e collo contano sullo
  stesso orologio della geometria. Il collo (rottura a 0,38 di 2 × `settleMs`) si rompe a ~380 ms, dopo il colmo del
  superamento. Chi si sposta soltanto resta a 300 ms.
- **L'attesa**: per i primi 0,17 (85 ms) un donatore che deve spostarsi non viaggia (`waitMs`); chi nasce è rilasciato
  alla fine dell'attesa se il donatore resta dov'è, 0,12 dopo se va lontano almeno quanto è alto (in proporzione, se va
  meno lontano), poi +0,09 per ogni fratello più vicino. Un viaggio ripartito a metà non aspetta.
- **La molla di rilascio** ha lo smorzamento 0,6 e il mezzo periodo 0,6 del tempo fra il rilascio e la fine, su tutti i
  lati: il vetro trasla **rigido** (il lato di coda non si ferma più all'arrivo) e supera il posto del 9,5% della corsa.
  Lo scarto si spegne fra 0,8 e 1 del bilancio.
- `GlassPlanePieceFrame.swell`: il **rigonfiamento in più** di chi è stato rilasciato (0,9 del rigonfiamento del
  carattere, fra l'arrivo e la fine, col colmo a metà). Non è nei riquadri del piano: lo applica chi disegna il palco
  (`fluidGlassStagePlane`), insieme a quello comune.
- **Il dondolio** (`PlaneSway`): il donatore di una nascita che resta dov'è rincula verso chi esce di 0,077 del suo
  lato corto e rimbalza di 0,047 dall'altra parte; chi accoglie un vetro che confluisce è spinto tutto intero
  dell'8,4% della corsa di quello (al più 0,2 del lato corto) e torna. Chi confluisce non esce più dall'altra parte del
  vicino. Un vetro che dondola non è `still` (il suo contenuto lo segue), ma per i campi conta come un vetro al suo
  posto: non tira un collo al vicino fermo che ha dall'altra parte.
- Il **contenuto** di ogni pezzo va disegnato al centro del suo vetro in volo (`fluidGlassContentFlightOf`): anche
  quello di chi nasce e di chi confluisce, che prima restava al posto d'arrivo o di partenza.
- `GlassPlanePieceFrame.contentPhase`: la fase su cui entra il contenuto. È `phase` per tutti; per chi nasce da un
  vicino è avanti di quanto il primo rilascio del viaggio viene prima di 0,29 (0,12 quando il donatore resta dov'è).

## F3.12c: l'orologio corretto, la coda di Apple, la goccia con la vita

Le sei tracce sono state riesportate dal Kotlin dopo F3.12c e ora hanno **80 fotogrammi** (da −83 a 1233 ms: una nascita
ripensata a 150 ms diventa una confluenza con la coda di chi accoglie, ferma a ~1050, e servono cinque fotogrammi fermi
dopo). `contesto-capsule` e `comparsa` hanno i primi 53 fotogrammi identici a prima, più 27 fermi: chi si sposta soltanto
e chi compare per lente non cambiano. Le `collo-*` arrivano a 1000 ms (1300 quelle ripensate a metà). Le misure di Apple
sull'orologio corretto sono in `artifacts/liquid-glass/sessioni/f3-12c/misure.md`; le tracce delle scene col contenuto,
con la luce e con la geometria di Apple sono in `traces/f3-12c/`. Cosa cambia nel contratto, rispetto a F3.12a:

- **Il riposo a vista (`settleMs`) è il ritorno del superamento**, 540 ms (`FluidMotion.PlaneReleaseMillis`): contenuto,
  luce, alone e collo contano lì (il collo si rompe a ~415 ms). **La fine del viaggio (`durationMs`) viene dopo**: la
  molla di chi è rilasciato fa la sua coda (lo 0,9% della corsa dall'altra parte a ~610 ms) e finisce quando la sua busta
  e quella del suo rigonfiamento sono sotto il mezzo pixel; il donatore e chi accoglie hanno il loro dondolio a chiavi
  (`PlaneSway`), fermo a ~860 e ~900 ms.
- **L'attesa** dura 0,23 (124 ms); chi nasce da un donatore che va lontano è rilasciato 0,08 dopo.
- **Chi nasce parte piccolo** (0,8 della sua misura dentro il donatore, `PlaneReleaseBud`) e cresce con la molla; **chi
  confluisce** va verso un posto dentro il vicino più basso di lui (0,66 di traverso, `PlaneMergeCore`) e, quando è
  dentro, diventa il vicino (`PlaneMergeCoreRegrowFrom` e `…To`: niente «orecchie» nell'unione).
- **Il rigonfiamento proprio** (`GlassPlanePieceFrame.swell`, anche negativo) e **la parte del comune**
  (`GlassPlanePieceFrame.swellShare`, zero per chi nasce, chi confluisce, il donatore e chi accoglie): il contenitore
  disegna `swellShare · comune + swell`.
