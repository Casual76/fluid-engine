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
