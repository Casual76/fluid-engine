# AndroidLiquidGlass (backdrop)

`engine-ui` include, **come sorgente**, la libreria che disegna davvero il vetro del Fluid Glass.

- Progetto: **AndroidLiquidGlass** (`backdrop`), di **Kyant** ([@Kyant0](https://github.com/Kyant0))
- Repository: https://github.com/Kyant0/AndroidLiquidGlass
- Artefatto pubblicato: `io.github.kyant0:backdrop` (v2.0.0)
- Commit da cui abbiamo copiato: `b18eb0ff12c616546a68c72e7d0097f1ab286c87`
- Licenza: **Apache License, Version 2.0** — testo: https://www.apache.org/licenses/LICENSE-2.0
- Copyright 2025 Kyant

## Cosa abbiamo preso

| Dove sta da noi | Cosa è | Da dove viene |
|---|---|---|
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/glass/backdrop/**` | il renderer: cattura del backdrop, catena di `RenderEffect`, shader AGSL della lente e del bordo speculare, ombra interna ed esterna | il modulo `backdrop` della libreria |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/glass/interaction/GlassInteraction.kt` | le primitive di gesto: riconoscimento del drag senza soglia, molle di pressione e schiacciamento, evidenziazione che segue il dito | i componenti d'esempio dell'app *catalog* dello stesso repo |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/FluidSwitch.kt` | `FluidSwitch`: il pomello come unica lastra, la copia schiacciata della pista che rifrange, le molle e lo stiramento con la velocita' | `LiquidToggle` dell'app *catalog* |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/FluidSlider.kt` | `FluidSlider`: la pillola bianca che a riposo e' opaca e sotto il dito diventa lente, la pista registrata nel suo layer e la copia che si dispiega, le molle e lo stiramento | `LiquidSlider` dell'app *catalog*, arrivato passando da un port gia' fatto in Pampa notes |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/glass/interaction/GlassDrop.kt`, `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/FluidGlassMenuPanel.kt` (`FluidMenuDrop`) | la goccia: la ricetta del pomello dello slider e dell'interruttore (sfocatura 8 dp, lente 10/14 e 5/10 dp, `Ambient` ÷ 1,5, ombra interna 4 dp, ombra fissa 4 dp al 5%, lo schiacciamento dalla velocita') tolta dai due controlli che ne portavano una copia ciascuno; dalla 3.0 ne legge lo schiacciamento la pillola della tab bar, e la ricetta intera, con i numeri del pomello dello slider, e' la goccia dei menu e dei pulsanti degli avvisi | `LiquidSlider` e `LiquidToggle` dell'app *catalog*, stesso commit |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/FluidFloatingTabBar.kt` | il puck di vetro trascinabile, la riga nascosta che il puck campiona attraverso se' stesso, lo stiramento che segue il dito | `LiquidBottomTabs` / `LiquidBottomTab` dell'app *catalog*, innestati sul `FloatingTabBar` di Elyes Mansour (anche lui Apache-2.0, citato nell'intestazione del file) |

Ogni file copiato porta in testa l'avviso Apache-2.0 e l'elenco delle modifiche, come richiede la
sezione 4 della licenza.

## Perché è copiata invece che dichiarata come dipendenza

La libreria è pubblicata su Maven Central ed è **Compose Multiplatform**. L'engine è Android puro e
finisce dentro app che dichiarano le proprie dipendenze Compose: aggiungere un artefatto KMP a
cinque `build.gradle` diversi, ognuno con la propria BOM, è un modo affidabile di rompere un'app
alla volta. Le sorgenti copiate compilano contro la BOM che l'app ha già.

Il prezzo è che gli aggiornamenti a monte vanno riportati a mano. Per renderlo possibile i file
sono tenuti il più vicino possibile all'originale, e ogni divergenza è marcata nel codice con un
commento che comincia con **`Fluid Engine change:`** — bastano quelli per fare il diff con una
versione nuova.

Le modifiche fatte finora:

1. **KMP fuso in un solo source set Android.** Le coppie `expect`/`actual` sono state unite:
   sopravvivono i corpi Android, `expect` e `actual` spariscono.
2. **Rinominato il package** `com.kyant.backdrop` → `dev.antigravity.fluidengine.ui.glass.backdrop`.
3. **Tolto il supporto per `io.github.kyant0:shapes`** nella lente: l'engine ha già le sue forme a
   raccordo continuo (`ContinuousCornerShape`) e non voleva una seconda dipendenza per le stesse.
4. **`recordLayer` prende il nodo come parametro** invece che come *context parameter*: quella
   funzione di Kotlin è ancora sperimentale, e accenderla per `engine-ui` la accenderebbe per ogni
   app che ospita il modulo.
5. **La lente non lancia più eccezioni.** A monte, dare al vetro una forma che il campo di distanza
   non sa descrivere è un errore del programmatore e solleva `UnsupportedOperationException`. Qui
   il rettangolo (una barra a tutta larghezza — il vetro più ordinario che esista) è gestito come
   una forma a raggi zero, e qualsiasi altra forma non descrivibile perde la rifrazione e tiene
   sfocatura e vividezza. Un design system che finisce in cinque app deve degradare, non chiudersi.
6. **`@SuppressLint("NewApi")` sulla cache degli shader**, con il contratto scritto accanto: la
   libreria a monte è un modulo multipiattaforma e non passa mai dal lint di Android.
7. **Le superfici grandi non muoiono e non strozzano.** La cattura del backdrop si stringe da sola
   quando la superficie supera il tetto delle texture GPU (`fitToTexture`, altrimenti torna vuota e
   si vede un rettangolo nero), e il compositing del nodo è `Auto` invece di `Offscreen`: la pila
   del vetro è tutta `SrcOver` e l'isolamento non cambiava un pixel, ma ri-rasterizzava l'intero
   pannello a ogni fotogramma di scorrimento.
8. **`ShapeProvider.fastClipShape` riconosce `GlassClipGeometry`** (Fluid-physics, 1.9.0): una
   sagoma in trasformazione consegna al layer il proprio clip — il rettangolo del nodo — perché la
   silhouette vera la ritaglia l'alpha dello shader, e leggere i raggi d'angolo da una forma che
   cambia a ogni fotogramma ri-clipperebbe il layer a ogni fotogramma.
9. **Il preludio SDF vive anche in una copia first-party.** `FluidPhysicsShaders.kt`
   (`ui/fluidphysics`) ri-dichiara `radiusAt`/`sdRoundedRect`/`gradSdRoundedRect` derivandoli da
   `Shaders.kt` (dove sono `private`), e ci costruisce sopra la famiglia di Fluid-physics: i
   rettangoli fusi con lo smooth minimum e il campo poligonale ad anello di vertici. Stessa
   struttura di rifrazione (`circleMap`, banda di displacement al bordo), perché il materiale deve
   restare lo stesso vetro anche mentre cambia forma. Apache-2.0, Copyright 2025 Kyant, come il
   resto.
10. **Una pressione arriva sempre in fondo** (2.11.0, `GlassInteraction.kt`). A monte un rilascio
    riparte da dove la pressione era arrivata, e un tap rapido accende il vetro di un decimo. Qui
    la luce di `GlassTouchHighlight` passa dalla corsa di pressione dell'engine (`FluidPressMotion`,
    first-party) e puo' essere guidata da fuori (`progress`, `touch`), e il rilascio di
    `GlassDragAnimation` aspetta che la pressione sia arrivata prima di sgonfiare il pomello.
    Dalla 2.12.0 la ricetta della luce sta in `glowRecipe`, e con `onGlass` una superficie di vetro
    chiara riceve luce bianca additiva, piu' leggera e stretta di quella della sorgente, invece
    dell'inchiostro nero pensato per le righe opache.

11. **I port dei controlli del catalogo** (`FluidSwitch.kt`, `FluidSlider.kt`). Le costruzioni e i
    numeri sono quelli della sorgente; cambia soltanto cio' che l'engine chiede a ogni controllo:
    l'accento del tema al posto del colore fisso di iOS (e per lo slider il velo della pista preso da
    `onSurface`, con alpha ricalcolati perche' cada dove cade il grigio originale), `enabled`, il
    tocco aptico, la semantica per TalkBack, il movimento ridotto, un ripiego senza `RenderEffect`.
    In `FluidSlider` in piu': lo stiramento usa il modulo della velocita' invece del segno (nella
    sorgente un verso accorcia la pillola), la pista e la pillola stanno dentro i limiti del
    controllo (un quarto di pillola di margine per lato, invece di sporgere), il gesto sta su tutta
    la riga e le tacche di scatto sono un'aggiunta nostra. `FluidSwitch` e `FluidSlider` consumano
    il puntatore della pillola trascinata, cosi' una pagina che scorre non glielo contende.
    Dalla 2.13.1 `FluidSwitch` non usa piu' il rilevatore di trascinamento della sorgente
    (`DragGestureInspector`) ma un gesto suo su tutto il controllo, come lo slider: tocco e
    trascinamento da qualunque punto, una spinta veloce che conta, e la vibrazione al passaggio della
    meta'. Le molle (`GlassDragAnimation`) restano quelle della sorgente.

12. **Vetro su vetro** (2.13.0). Un nodo di `drawBackdrop` e' anche un contenitore che gli altri
    nodi trovano fra gli antenati (`TraversableNode`): pubblica il proprio materiale — sfondo,
    cattura, superficie, contenuto escluso — in un layer creato al primo lettore, e campiona quello
    del contenitore in cui sta, infilato nella propria pila (`Backdrop.stackedBackdrops`,
    `Backdrop.forEachPart`). Un `LayerBackdrop` sa chi lo registra e puo' essere registrato solo su
    richiesta (`onDemand`, conto dei lettori); il materiale esportato si registra di nuovo solo
    quando cambia, e la manutenzione della cattura e' separata dal suo disegno, cosi' l'export non
    la ripete. La logica sta in un file nostro, `GlassNesting.kt`, accanto ai sorgenti copiati.

13. **`radiusAt` riceve la coordinata centrata** (`internal/Shaders.kt`, pacchetto F0.2 di
    `docs/liquid-glass/piano.md`). La funzione sceglie il raggio dell'angolo dal quadrante del punto,
    ma a monte i quattro shader che la usano (le due lenti e i due bordi di luce) le passano la
    coordinata del layer, che non e' mai negativa: ogni angolo prendeva il raggio in basso a destra.
    Con quattro raggi uguali non si vede; con raggi diversi (il foglio di `FluidSheet` e di
    `FluidGlassModal`, arrotondato solo in alto, il primo e l'ultimo segmento di un gruppo) lente e
    bordo seguivano la curva sbagliata. Ora le passano `centeredCoord`, la stessa coordinata che
    riceve `sdRoundedRect`, che nella lente comprende gia' il margine della cattura (`offset`). La
    prova e' sul banco: `tools/glassbench/scenes/f0-raggi-diversi.json`. Il difetto e' ancora a
    monte (rilevato dalla ricerca R3, `docs/liquid-glass/ricerca/fattibilita-android.md`).

14. **L'anello configura il pennello dentro la propria registrazione** (`highlight/HighlightModifier.kt`).
    La lambda passata a `GraphicsLayer.record` non si esegue una volta sola: Compose la riesegue
    da se' quando la piattaforma ha buttato la display list del layer (un anello rimasto un
    fotogramma senza disegno, un'app tornata dallo sfondo). Nell'engine lo shader del riflesso e'
    un'istanza condivisa da tutto il processo (`RuntimeShaderCacheImpl`) e l'anello si registra una
    volta sola, non a ogni disegno: due nostre modifiche, ciascuna con la sua ragione nel sorgente.
    Insieme rendevano dannoso un dettaglio innocuo a monte, la configurazione del pennello fatta
    prima di `record`: la riesecuzione disegnava l'anello con taglia e raggi dell'ultima superficie
    che aveva scritto gli uniform. Ora la configurazione sta dentro la lambda, con la sagoma letta
    alla registrazione: uniform e disegno nello stesso istante. A monte non fa danno, perche' ogni
    nodo ha la sua istanza di shader e ri-registra a ogni disegno. Trovato cercando la causa del
    contorno fantasma di Fluid-physics (`docs/liquid-glass/ricerca/contorno-fantasma.md`, sezione 9);
    la prova sul JVM e' `HighlightRingReplayTest`.

15. **Uno shader rifiutato dal dispositivo fa ripiegare, non cadere** (`effects/Lens.kt`,
    `highlight/HighlightStyle.kt`, `RuntimeShaderCache.kt`, e il bagliore di `GlassInteraction.kt`, che
    deriva dal catalogo). A monte la lente, i due riflessi e il bagliore guardano solo il livello di API
    e poi costruiscono il `RuntimeShader`: su un dispositivo con Android 13 o piu' il cui compilatore
    rifiuta la sorgente, il costruttore lancia dentro il disegno. Qui ognuno chiede prima il verdetto
    della propria sorgente alla sonda dell'engine (`GlassFieldHealth.usable`, una compilazione per
    processo e poi una lettura) e, su un rifiuto, prende il ramo che ha gia' sotto API 33: niente
    lente, riflesso senza shader, bagliore piatto. La cache condivisa guadagna la porta
    `obtainEngineShader` (`null` invece di un'eccezione, per le sole sorgenti dell'engine; la
    `obtainRuntimeShader` pubblica resta quella di monte) e diventa `internal`, perche' la sonda compila
    li' dentro e la prova non costa una compilazione in piu'. La sorgente del bagliore, che a monte e'
    una stringa scritta dentro la chiamata, e' stata spostata alla lettera nella costante
    `GlassGlowShaderString` (`glass/field/GlassFieldHealth.kt`), cosi' la provano anche il banco e il test
    strumentato; il bagliore prende la sua istanza dalla cache condivisa, come lente e riflessi. Dove le
    sorgenti compilano, il disegno chiede gli stessi shader, con le stesse sorgenti al carattere e gli
    stessi uniform. Pacchetto F0.11 di `docs/liquid-glass/piano.md`,
    dal rilievo 1 della review di F0; la prova sul JVM e' `GlassShaderFallbackTest`.

16. **`drawBackdrop` guadagna la luce e un posto sotto la superficie** (4.0.0, `DrawBackdropModifier.kt`,
    pacchetto F1A.6 di `docs/liquid-glass/piano.md`). Due parametri in coda, con un default che lascia
    il nodo com'era per chi non li passa:
    - `onDrawLight`, disegnato dopo `onDrawSurface` e prima del contenuto, e **fuori** dalla
      registrazione del materiale esportato. A monte tutto cio' che una superficie disegna sopra la
      propria cattura entra nel materiale che il vetro annidato campiona. Il materiale nuovo dell'engine
      (`glass/field/`) ha una luce (filo, riflessi, bagliore del dito) che non deve entrarci: un tasto su
      un pannello rifrangerebbe il bordo del pannello, e un bagliore che cambia a ogni fotogramma
      rifarebbe la catena di ogni vetro che gli sta dentro;
    - `underlay`, un `Modifier` posato fra il layer di `layerBlock` e il nodo: dentro la trasformazione
      della superficie e fuori dal suo ritaglio, cioe' dove a monte stanno gia' `ShadowElement` e
      `HighlightElement`. Serve all'ombra del materiale nuovo (`Modifier.glassShadow` di
      `glass/field/GlassShadow.kt`), che non e' una `Shadow` di questa libreria e che altrimenti
      resterebbe fuori dal layer di un tasto che cresce sotto il dito.

    Nessuna riga di monte cambia comportamento: con i default (`null` e `Modifier`) il nodo disegna e
    registra esattamente cio' che disegnava. Il vetro di sempre (modello `Classic` dell'engine) non li
    passa.

17. **Il nodo si conta, e il livello di API si puo' simulare in debug** (4.0.0, `DrawBackdropModifier.kt`,
    `Platform.kt`, pacchetto F1A.7 di `docs/liquid-glass/piano.md`).
    - `DrawBackdropNode` tiene un `GlassNodeCensus` (classe dell'engine, `FluidGlassDiagnostics.kt`): il nodo
      e la sua cattura all'attacco, il layer della posa alla prima posa, il materiale proprio quando nasce,
      la superficie con il suo materiale (nuovo se passa `onDrawLight`), tutto restituito al distacco; un
      aggiornamento dell'elemento riconta la superficie. A monte non c'e' modo di sapere quanti nodi e layer
      del vetro sono vivi. A diagnostica spenta ogni evento costa una lettura volatile, e il disegno non
      cambia di un pixel.
    - `isRenderEffectSupported()` e `isRuntimeShaderSupported()` chiedono `glassApiLevel()` invece di
      `SDK_INT`: e' `SDK_INT`, salvo in un'app `debuggable` a cui `FluidGlassDiagnostics.simulateApiLevel`
      ha chiesto di rispondere come un Android piu' vecchio. Il livello simulato puo' solo scendere, quindi
      un `true` vuol dire ancora che la piattaforma ha l'API (il contratto di `@ChecksSdkIntAtLeast` regge);
      in un'app di rilascio il campo resta vuoto e le due funzioni rispondono come a monte.

18. **Lo scope degli effetti sa se la superficie sta dentro un vetro** (4.0.0, `DrawBackdropModifier.kt`,
    rifinitura di F1A di `docs/liquid-glass/piano.md`, difetto 4 della sessione S1-ter). Lo scope che il nodo
    passa agli `effects` implementa anche `GlassNestingScope` (interfaccia interna dell'engine, nello stesso file):
    `standsOnGlass` e' la risposta che il nodo gia' si da' per il vetro su vetro della voce 2.13.0 (`planNesting`:
    il materiale del pannello che lo contiene infilato nella pila, o gia' cablato a mano nel fondale), tenuta in un
    campo del nodo; `glassStandsOnGlass` e' la regola, pura. Quando la risposta cambia (all'attacco, o quando il
    pannello di sopra compare o se ne va) il nodo rifa' gli effetti, ma solo se hanno gia' una taglia e solo per
    una superficie che passa `onDrawLight` (il materiale nuovo, l'unico che la legge); al distacco torna falsa.
    Serve al materiale nuovo (`glass/field/GlassFieldEffects.kt`) per non posare il proprio velo pieno sopra un
    materiale che ne ha gia' uno: un tasto in una notifica tinta, sul tema scuro, ne usciva una toppa grigia. Lo
    scope pubblico (`BackdropEffectScope`) non cambia, nessuna firma pubblica cambia, e chi non fa il cast non
    vede niente: il vetro di sempre (modello `Classic`) non rifa' gli effetti e disegna al pixel come prima.

19. **La pillola della barra flottante e la sua riga nascosta sul materiale nuovo** (4.0.0, `FluidFloatingTabBar.kt`,
    pacchetto F1B.4 di `docs/liquid-glass/piano.md`). Dove il vetro e' quello nuovo (`FluidGlassModel.Liquid`: Android
    13, sorgenti sane, una forma che il campo sa rendere) il puck e la riga tinta nascosta che il puck campiona mentre
    e' premuto non usano piu' la catena di Kyant (`vibrancy`/`blur`/`lens`, `Highlight`, `Shadow`, `InnerShadow`, la
    pellicola d'accento in `onDrawSurface`) ma le superfici della ricetta dell'engine (`GlassLiquidSurface`) coi
    profili di `glass/field/GlassPuckProfile.kt`: la pillola a riposo rifa' dalla pagina cruda il materiale della barra
    con sopra una selezione piu' scura, premuta posa la sola selezione sopra la riga, che e' il materiale della barra.
    Le modifiche stanno in una classe privata nuova in fondo al file (`LiquidPuck`, `rememberLiquidPuck`) e in cinque
    punti marcati `LOCAL CHANGE (F1B.4)` dentro `ExpandedTabs`: la scelta del materiale, un ramo davanti alla
    `drawBackdrop` della riga nascosta e uno davanti a quella del puck, il colore che cresce mentre il puck compare,
    e l'inchiostro dell'icona ridisegnata sopra il puck (quello della riga nascosta, al posto dell'accento sul puck).
    Geometria, molle, trascinamento, squash, il momento in cui la riga nascosta esiste e il fermo della cattura non
    cambiano. Il ramo di Kyant resta intero e identico, e lo prende ogni barra che resta al vetro di prima.

20. **Ogni giro degli effetti e' osservato** (4.0.0, `DrawBackdropModifier.kt`, giro di correzione unico di F1B di
    `docs/liquid-glass/piano.md`; rilievi R1 e 7 dei critici). A monte `DrawBackdropNode` rifa' gli effetti all'attacco
    e quando cambia uno stato letto in un giro precedente, sotto `observeReads`, e nel disegno quando lo scope cambia
    (taglia, densita', scala della cattura) **fuori** dall'osservazione. All'attacco la taglia non c'e' ancora, e un
    effetto che senza taglia esce presto legge poco o niente: le letture del giro del disegno, quello che legge tutto,
    non rifacevano mai la catena. Ne usciva un corpo fermo mentre la luce (che si ridisegna) andava avanti, e la barra
    fra le pagine senza vetro nei ritorni. Ora il nodo passa per `BackdropEffectsUpdates` (classe interna in fondo allo
    stesso file): i tre giri chiamano lo stesso `observeReads { updateEffects() }`, anche quello del disegno. Tre punti
    marcati `LOCAL CHANGE (F1B, voce 20)`: il campo del nodo, il disegno, `observeEffects`. Nessuna firma pubblica cambia;
    a parita' di stato gli effetti producono la stessa catena di prima (il vetro di sempre disegna al pixel come prima),
    cambia solo che, quando uno stato letto dopo la taglia cambia, la catena si rifa'. La prova e'
    `BackdropEffectsUpdatesTest` (sul JVM, con un osservatore di snapshot vero e le superfici del materiale nuovo): con
    la regola di monte fallisce.

21. **Le sorgenti dicono quando si registrano di nuovo** (4.0.0, `LayerBackdrop.kt`, `LayerBackdropModifier.kt`, F2.1).
    Una superficie che campiona un layer non ricattura quando il layer cambia: sotto un vetro fermo lo sfondo puo'
    passare da bianco a nero senza che nessuna cattura lo sappia, e lo sa solo chi registra. Alla sonda dello sfondo
    (`glass/field/GlassProbe.kt`) serve saperlo. `LayerBackdrop` guadagna un contatore (`revision`), gli ascoltatori
    (`LayerRevisionListener`, `addRevisionListener`, `removeRevisionListener`) e `announcesRevisions`;
    `LayerBackdropNode` chiama `bumpRevision()` dopo ogni registrazione, non quando e' tenuto fermo da `frozen`;
    `isRecordedByLayerBackdropNode()` dice chi registra. Tutto interno, marcato `Fluid Engine addition (4.0.0, F2.1)`;
    nessuna firma pubblica cambia; senza iscritti l'avviso costa un incremento. I layer pubblicati dai pannelli
    (`exports`), che registra `DrawBackdropNode`, non avvisano: la sonda li legge a cadenza fissa.

## Cosa fare in pratica

Tenere questo file nel repo e riportare **AndroidLiquidGlass — Apache-2.0 — Copyright 2025 Kyant**
fra le licenze di terze parti nella schermata "informazioni" dell'app. L'engine la fornisce già
pronta: `FluidLicensesSection` in `engine-ui` elenca da sola tutte le voci di
`FluidEngineLicenses`, e quindi anche questa.

## E Square

L'implementazione precedente del vetro era nostra e non funzionava: rifrazione finta dipinta in un
bordo di due dp, opacità alzata per compensare, nessuna risposta al dito. Che la strada giusta
fosse la libreria di Kyant — e che si potesse copiare come sorgente invece che dipendere dal KMP —
l'abbiamo imparato da **[Square](https://github.com/Lelonio/Square)** di
[@Lelonio](https://github.com/Lelonio), un client musicale Android che l'ha fatto per primo e bene.

Da Square non è stato copiato codice: il suo repo è GPL-3.0 e il suo vetro è la stessa libreria
Apache-2.0 che abbiamo preso direttamente da monte. Il debito è di indirizzo, non di righe, ed è
segnalato qui e nel `README.md` perché è comunque un debito.
