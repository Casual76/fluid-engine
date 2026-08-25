# Changelog

Le versioni seguono il semantic versioning: **patch** correzioni, **minor** aggiunte compatibili,
**major** rotture. Le voci che richiedono una modifica nelle app che aggiornano sono marcate
**BREAKING** — `engine-update.ps1` le evidenzia mentre aggiorna.

<!-- nuove versioni qui sopra -->

## 1.8.1 - 2026-08-25

- FluidSectionIndex non ruba piu' gli scorrimenti. Occupava una casella di 48x136 dp appoggiata al bordo destro, all'altezza esatta in cui sta il pollice mentre scorre, e un nodo che riceve un tocco lo riceve e basta: anche senza consumarlo, la lista sotto non lo vedeva mai, perche' sono fratelli e il test di collisione si ferma al primo che colpisce. Finiva li' circa un gesto su cinque, e la lista saltava a una sezione invece di muoversi. Ora il bersaglio e' 28 dp per l'altezza della barretta, e viaggia con lei; e si prende il gesto **solo tenendo premuto** - chi vuole scorrere si muove subito, chi vuole l'indice si ferma
- E a riposo e' disegnata una barretta sola, che dice dove sei nella lista, invece di un grappolo di otto tacche che chiedeva di essere usato. Tenendola premuta si apre in un nastro con le stazioni, alto poco piu' della meta' dello schermo invece che quanto tutto il display: otto stazioni distribuite sull'intera altezza obbligano il pollice ad attraversare il telefono per attraversare l'archivio, che e' il movimento che una scorciatoia dovrebbe evitare
- Il pop-up non schiaccia piu' il testo. Partiva dal rettangolo esatto dell'ancora, cioe' scalava una card alta 300 dp fino a una riga alta 90 tenendone la larghezza: due fattori diversi sui due assi, e per il primo terzo di ogni apertura il titolo era anamorfico. Ora il contenuto non si trasforma affatto - viene disposto una volta alla sua misura finale - e quello che viaggia e' la *finestra* da cui lo si vede, piu' uno zoom uniforme di pochi punti percentuali. E' anche cio' che fa davvero l'apertura di un'app su iOS
- L'ancora smette di disegnarsi mentre il suo pop-up e' aperto (fluidExpandOrigin prende open). Il pannello e' vetro, quindi la riga si vedeva attraverso: due bordi uno dentro l'altro dove doveva essercene uno, e il titolo leggibile due volte. Resta nascosta per tutta l'uscita, non fino alla richiesta di chiusura: FluidGlassModalHostState.isOnScreen e' il segnale che comprende anche l'animazione
- La qualita' del vetro scende anche durante le transizioni di rotta, non solo durante lo scorrimento. Il cambio di scheda e' il fotogramma piu' caro dell'app per un motivo strutturale: mentre due pagine si attraversano ne esistono due, quindi due fondali ambientali e due serie di pannelli, disegnati insieme nel momento in cui il budget e' gia' speso


## 1.8.0 - 2026-08-25

- Interazioni: portale con item (uscita vera dei pop-up), morph dal rettangolo dell'ancora, segmented con tocco che segue la pillola, indice sezioni a rotaia con lente, menu Expand sui tasti barra, clip degli angoli coerente con la tinta, sorgenti backdrop che invalidano al movimento.


## 1.7.0 - 2026-08-25

- FluidFoldingTabBar prende foldAlignment: Start, Center (com era) o End. Ripiegata la capsula resta l unico bersaglio della navigazione, e dove si posa decide se il pollice ci arriva


## 1.6.1 - 2026-08-25

- FluidPillTabs e il controllo segmentato dell engine, non un secondo disegno. Era l ultima superficie rimasta a fingere il rilievo - un guscio solido con una pastiglia bianca e un ombra sotto - mentre FluidSegmentedControl tiene una copia invisibile delle etichette tinte d accento sotto una lente: il segmento scelto non viene colorato, viene visto attraverso il vetro, ed e trascinabile fra i segmenti. Il nome resta, quindi le app non cambiano una riga


## 1.6.0 - 2026-08-25

- Il vetro smette di ridisegnare quello che non e cambiato. Sul tablet la Bacheca passa da 300-550 ms per fotogramma a 53 (senza vetro sarebbe 40: il materiale costa 13 ms invece di 260), i Voti da 400 a 21. La sorgente del backdrop si compone offscreen, quindi la pagina si rasterizza una volta invece che una per ogni pannello che la campiona; i layer di bordo e ombra non si ri-registrano a ogni draw e sono limitati in area, cosi il budget GPU di HWUI non si sfonda piu; i clip dei layer e le forme piccole usano rettangoli arrotondati, che la GPU sa clippare da sola, invece di path generici che passano dall atlas di maschere della CPU; gli shader AGSL sono condivisi da tutto il processo invece di essere ricompilati da ogni pannello
- FluidGlassQuality: il materiale si assottiglia mentre la pagina corre e torna intero quando si ferma. Durante un lancio se ne vanno lente, dispersione, ombre e meta della sfocatura, e la catena ottica si registra a meta risoluzione: nessuna di quelle cose e visibile a centoquaranta pixel per fotogramma, e sono esattamente quelle che costano. La tinta non si tocca mai. FluidScreen lo aggancia da solo, quindi ogni schermata lo eredita
- GlassOptics.backdropResolution: quanto della propria risoluzione una superficie concede alla cattura. Il ruolo Content sta a 0.5, perche quello che rifrange e la lavata ambientale e un gradiente ricampionato a meta torna su identico


## 1.5.4 - 2026-08-25

- Il morph del titolo usa ancore locali allo schermo invece che alla finestra. Su tablet ogni pagina e' rientrata della larghezza del rail di navigazione, e la differenza fra i due sistemi diventava una traslazione: il titolo grande a riposo finiva sotto il rail. Qualunque inset l'app metta attorno alla pagina ora si annulla da solo


## 1.5.3 - 2026-08-25

- Il pannello di vetro compone in place: il compositing Offscreen del nodo ri-rasterizzava l'intero pannello, testo compreso, a ogni fotogramma di scorrimento, per un isolamento che su una pila tutta SrcOver non cambiava un pixel. Sul tablet la pagina Voti passa da 85 a 27 ms per frame (p90). Sparito il layer offscreen, sparisce anche la seconda texture che poteva sfondare il tetto della GPU
- GlassOptics.backdropResolution: quanto della propria risoluzione una superficie concede alla cattura del backdrop, sopra la scala guidata dal blur. Il ruolo Content scende a 0.5, perche' quello che rifrange e' la lavata ambientale e un gradiente ricampionato a meta' torna su identico. Tutti gli altri ruoli restano a 1


## 1.5.2 - 2026-08-25

- La difesa dal tetto delle texture copre anche il contenuto del pannello, non solo la sua cattura. Un pannello di vetro compone se stesso attraverso un layer offscreen grande quanto se stesso, e quel layer e' una texture come la cattura del backdrop: oltre il tetto non si alloca, e le righe del pannello spariscono lasciando un vetro rifratto senza niente stampato sopra. Ora un pannello fuori misura rinuncia al compositing offscreen, che a quella scala nessuno distingue, invece che al proprio contenuto


## 1.5.1 - 2026-08-25

- Una superficie di vetro piu' grande di una texture non annerisce piu'. Il layer che ogni pannello registra e' una texture della GPU, e oltre il tetto la registrazione torna vuota: sotto una tinta traslucida diventa un rettangolo nero al posto del contenuto. Ci arriva sempre un gruppo lista, perche' un gruppo e' alto quanto le righe che uno si trova ad avere - ottantaquattro voti sono sedicimila pixel. Ora l'engine registra piu' in piccolo invece di sparire (fitToTexture), e quello che c'e' dietro un pannello di contenuto e' una lavata morbida che a un quarto della risoluzione non perde niente


## 1.5.0 - 2026-08-25

- FluidScreen prende un canvas ambientale: FluidScreen(ambient = FluidAmbient(tone, motif)) dipinge sotto la pagina tre lavate radiali sull accento piu il motivo dell hero. Serve perche il vetro sopra una pagina grigia piatta e invisibile per costruzione: una lente che rifrange una superficie piatta produce una superficie piatta
- Due registrazioni, non una. Il canvas si chiude prima che la lista esista, quindi il vetro nel contenuto rifrange solo la lavata e non puo contenere se stesso; la chrome rifrange canvas piu corpo impilati con rememberCombinedGlassBackdrop. La pagina nel suo insieme resta opaca e l invariante delle transizioni di rotta non si muove
- FluidCard, FluidListGroup e FluidMetricTile accettano glass = true. E una richiesta, non un obbligo: senza canvas in scope o sotto API 31 il componente disegna la superficie opaca di sempre. Il vetro va sul contenitore, mai sulla riga
- FluidGlassModalPortal e FluidGlassModalHost: modali dentro la radice, quindi il pixel esce sopra la tab bar e il vetro campiona davvero la pagina. Quattro presentazioni - Popover, Sheet (trascinabile in basso, con nested-scroll che negozia con la lista dentro), ContextMenu in stile iOS e Expand, che cresce dal rettangolo della riga che l ha aperto
- FluidGlassMenuButton e FluidFoldingTabBar: un pulsante che si apre nel proprio menu, e una tab bar che si chiude sulla scheda in cui sei mentre la pagina scorre. Ripiegata la coppia si raccoglie al centro invece di restare nell angolo del display
- La sfocatura scende ovunque: barra 10 dp, modale 7, pop-up 5, capsula flottante 3.6, controlli 2.8, contenuto 1.6. Il pop-up stava a 12 dp sopra uno scrim che aveva gia sfocato gli stessi pixel, e quello che ne usciva era una card grigio chiaro col bordo bianco. Il materiale e la piega, la brina serve solo a tenere leggibile il testo sopra
- Il menu contestuale non raddoppia piu ogni riga. La riga sollevata e un pannello di vetro e quella vera era ancora nella pagina sotto di lei, quindi arrivava una seconda volta rifratta e spostata; ora l ancora smette di disegnarsi mentre la sua copia e nell overlay e torna solo quando l overlay ha finito di uscire
- fluidContextMenuAnchor risponde al dito prima di aprire: la riga si gonfia e lascia la scia speculare dei controlli di vetro, invece di mezzo secondo di niente seguito da un overlay intero
- FluidSwitch: 56 dp invece di 51, cioe cinque dp in piu di corsa, e si gonfia mentre lo tieni premuto. A 51 una colonna di interruttori si leggeva come una texture sola
- L indicatore della FluidFoldingTabBar si trascina di nuovo fra le schede: la barra ripiegabile non gli aveva mai passato un onDrag, quindi la capsula era una fila di pulsanti con sopra una lente
- Le barre galleggiano a 16 dp dal bordo invece di 8: sopra la maniglia di sistema, non appoggiate sopra
- backdropScale: la catena di RenderEffect si registra a risoluzione ridotta al crescere del raggio, da 1.0 a 0.4. Sul dispositivo, build di release, scheda con piu vetro della galleria: 1.72% di fotogrammi in ritardo, 90esimo percentile 18 ms
- FluidSheet e deprecato in favore di FluidGlassModalPortal. ModalBottomSheet vive in una finestra di piattaforma separata e non puo leggere il GraphicsLayer dell app: non e un difetto, e un confine di sistema


## 1.4.0 - 2026-08-24

- Il Fluid Glass e rifatto sopra la libreria backdrop di Kyant (AndroidLiquidGlass, Apache-2.0), copiata come sorgente in engine-ui/ui/glass/. Rifrazione vera con campo di distanza, dispersione cromatica, bordo speculare orientato, ombra interna. Crediti in LICENSES/AndroidLiquidGlass.md
- La tinta del vetro scende a circa un terzo e la sfocatura di riferimento sale da 8 a 16 dp: la leggibilita si compra con il raggio, non con l opacita. E il motivo per cui il vetro precedente non sembrava vetro
- I controlli di vetro rispondono al dito: si inclinano verso il tocco con un tanh, si gonfiano e si schiacciano sotto pressione, e si illuminano dove sono stati toccati. L indicatore della tab bar si trascina fra le schede e si stira nella direzione in cui viaggia
- Un controllo appoggiato a una barra rifrange la barra e non la pagina: glassSurface accetta exports, rememberCombinedGlassBackdrop compone due materiali e la chrome espone LocalGlassBackdrop gia pronto
- ContinuousCornerShape arriva davvero alla capsula: era il raggio a cedere quando due angoli si sarebbero sovrapposti, e un angolo al 50 per cento si fermava al 62.5 per cento del lato. Ora cede lo smoothing, quindi ogni pastiglia dell interfaccia ha le estremita tonde
- FluidLicenses: fluidLicensesSection, FluidLicenseGroup e FluidLicenseDetails portano i crediti di terze parti nella pagina informazioni di ogni app
- sample/: la galleria del vetro, che si compila solo aprendo l engine da solo
- BREAKING: GlassOptics cambia vocabolario - refractionHeight, refractionAmount, blurScale, dispersion invece delle larghezze di rim. Nessuna app lo costruiva a mano
- BREAKING: glassControlSurface vuole il backdrop da rifrangere; la vecchia versione dipinta resta come fluidStaticGlassSurface per sheet e dialog, che stanno in una finestra propria e non possono campionare
- BREAKING: FluidGlassIconButton prende modifier prima di backdrop, e backdrop ha come default LocalGlassBackdrop


## 1.3.1 - 2026-08-24

- Le superfici live uniformi di tab bar, rail, notifiche e indici usano sagome circolari che coincidono esattamente con il campo di distanza AGSL.
- Rifrazione, magnification, doppio rim, specular e ombra interna sono più leggibili sul dispositivo reale; anche le lenti dei pulsanti hanno maggiore profondità.
- La base della navigazione conserva contrasto accessibile sopra contenuto arbitrario, lasciando al bordo crudo e dislocato il lavoro ottico.


## 1.3.0 - 2026-08-24

- GlassMaterial aggiunge bordi ottici, rifrazione del backdrop e profili Bar, Floating, Interactive e Modal con fallback progressivi per API e shape.
- FluidBarAction, back button, tab bar, notifiche e indici di sezione adottano lenti Fluid Glass senza blur annidati e con target touch da 48 dp.
- FluidScreen espone un overlay post-body per campionare correttamente il backdrop; sheet e alert usano un fallback ottico coerente nelle finestre modali.
- Il sampling resta ritagliato per superficie, sveglia ogni nuova anchor senza loop e crea lo shader runtime solo quando realmente supportato.


## 1.2.4 - 2026-08-24

- Il backdrop glass registra e sfoca solo le regioni realmente campionate, mantenendo blur, saturazione e texture invariati.
- Le maschere FadeDown riusano gli oggetti nello stato stabile e riducono le allocazioni durante lo scroll.
- Le superfici pressabili e i gruppi lista evitano RenderNode e clip ridondanti.


## 1.2.3 - 2026-08-24

- FluidScreen: il colore del contenuto vale anche per la barra. La 1.2.2 aveva avvolto solo la lista, e il titolo restava nero su fondo nero: quello che si vede mentre il titolo grande si ritira e la copia della barra, trasformata sull ancora del titolo, e la barra sta fuori dal corpo


## 1.2.2 - 2026-08-24

- FluidScreen dichiara il colore del contenuto. Dipingeva il proprio sfondo ma lasciava il testo a ereditare LocalContentColor, che fuori da un Surface vale nero: un app che non avvolge tutto l albero in un Surface si ritrovava il titolo grande nero su fondo nero. Chi dipinge il fondo dichiara anche cosa ci va sopra, altrimenti il componente funziona solo dentro l app da cui e stato estratto


## 1.2.1 - 2026-08-24

- Gli angoli circolari rimasti dentro l engine sono diventati continui: il gruppo di liste era a RoundedCornerShape(20.dp) mentre due righe piu sotto lo stesso file usava ContinuousCornerShape(FluidRadius.Group), la piastrella d icona e la barra a colonna erano circolari. Era la regola 1 del design system violata dentro l engine che la scrive
- Resta un solo angolo circolare, in RouteMotion, ed e deliberato: li il raggio cambia a ogni fotogramma e un angolo continuo costringerebbe il layer a ritagliare su un Path generico invece che su un rettangolo arrotondato, piu caro proprio durante l animazione che deve restare fluida. Ora c e scritto perche
- ADOZIONE.md alla radice tiene il piano per portare i componenti su tutte le app e lo stato di ognuna


## 1.2.0 - 2026-08-23

- FluidHero: l intestazione editoriale per una schermata piena di dati - un valore grande, fatti secondari che passano da tre a due a una colonna secondo lo spazio misurato e la scala del carattere, un motivo astratto sul fondo. Portata da ClasseViva, dove era rimasta fuori dall estrazione perche il suo enum si chiamava FeatureIdentity
- FluidHeroTone e FluidHeroMotif separano le due cose che quell enum teneva insieme: la posizione sull anello delle tre famiglie di accento, e la forma disegnata dietro. L app mappa le sue sezioni su tono e motivo, e il dominio resta nell app
- FluidHero: urgent promuove alla famiglia dell errore qualunque tono, non piu solo uno. Prima veniva ignorato in silenzio per sei identita su sette


## 1.1.1 - 2026-08-23

- Gli script leggevano i file con Get-Content -Raw, che in PowerShell 5.1 decodifica in ANSI e non in UTF-8: leggere e riscrivere corrompeva un po piu ogni carattere accentato a ogni esecuzione. Ha gia mangiato due trattini in questo file, e sarebbe successo al settings.gradle di qualunque app con un commento accentato


## 1.1.0 - 2026-08-23

- engine-install.ps1 -Modules: si scelgono i moduli da includere, e le dipendenze fra moduli si chiudono da sole. Senza, un app senza il plugin Compose nel build root non riusciva nemmeno a configurare il build: e la ragione per cui KeyVoice non poteva ospitare l engine
- engine.properties registra i moduli scelti; engine-doctor.ps1 verifica che il settings.gradle li rispecchi ed engine-update.ps1 non li perde piu aggiornando la versione
- EngineHttp.download verifica la dimensione attesa. Un download troncato non solleva niente e arrivava all installer come mezzo APK
- EngineHttp e open: leggere un manifest e decidere se una release e piu nuova sono logica ordinaria, e una classe final costringeva ogni app ospite a toccare la rete per testarla
- engine-update: il controllo che l APK sia davvero questa app estratto in rejectApk() e coperto da cinque test. Era inline nel flow, quindi verificabile solo su un dispositivo
- engine-update: STATUS_FAILURE_ABORTED non dice piu solo annullata. Verificato su un dispositivo reale: Play Protect blocca l installazione e Android riporta lo stesso codice
- modalita porto: un app che non puo ospitare i moduli (Compose Multiplatform) dichiara engine.mode=port e viene contata lo stesso
- versions.gradle leggeva una versione scritta a mano, ferma alla 1.0.0: ora la prende da ENGINE_VERSION
- engine-ui: rimossa un estensione Offset.times morta, gia coperta da un membro di Compose
- CHANGELOG: doppia codifica riparata, versioni dalla piu recente in giu


## 1.0.2 - 2026-08-23

- engine-install.ps1 -Mode copy: le cartelle build/ annidate finivano nella copia (centinaia di MB e percorsi troppo lunghi). Ora la copia passa da robocopy, che esclude a ogni profondita
- engine-install.ps1 esce con codice 0 esplicito: prima restituiva quello dell ultimo comando nativo, e chi automatizza l installazione lo leggeva come errore

## 1.0.1 - 2026-08-23

- versions.gradle: compileSdk e minSdk si lasciano sovrascrivere dal gradle.properties dell app ospite (engine.compileSdk, engine.minSdk), cosi un AGP piu vecchio non esclude l app dall engine
- engine-install.ps1: -Mode copy esiste davvero, e le righe di dipendenza escono nella sintassi del DSL dell app invece che sempre in Groovy
- docs: requisiti dell app ospite, submodule da repo locale (protocol.file.allow), cartella engine gia occupata, local.properties, e la ricetta per adottare il tema senza toccare i punti di chiamata
- la skill per gli agenti vive in skill/fluid-engine dentro questo repo, cosi non puo divergere

## 1.0.0 - 2026-08-23

Prima versione. Estratta da ClasseViva Expressive 7.1.1, ripulita di tutto quello che sapeva cos'e'
un registro scolastico.

- `engine-ui`: il design system Fluid completo — angoli continui, tipografia Inter con la scala iOS
  e la curva di tracking, palette derivata da un solo accento, liste raggruppate, motion unificato,
  materiale in vetro, notifiche in-app, tab bar, fogli, controlli.
- `engine-ui`: il colore del marchio e' un parametro (`FluidTheme(settings, brand)`) invece di una
  costante dell'app da cui l'engine e' nato.
- `engine-foundation`: `EngineSettings`, `SyncStatus` con il campo `notice` generico, confronto di
  versioni, modelli del manifest e risoluzione dei feature flag.
- `engine-config`: manifest remoto con flag, override per singola app, versione minima e kill
  switch; cache su DataStore e comportamento invariato quando la rete non risponde.
- `engine-update`: aggiornamento in-app via `PackageInstaller` sul modello Pampa Store, con canale
  stable/beta e verifica che l'APK scaricato sia davvero questa app a questa versione.
- `engine-widget`: palette Glance derivata dallo stesso schema colori dell'app, budget di layout per
  le celle del launcher e componenti (superficie, intestazione, gruppo, riga, hairline, pill).
- `engine-storage`: `EngineSettingsStore` e la cache del manifest.
- `tools/`: install, update, update-all, doctor, release.
