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

12. **Vetro su vetro** (2.13.0). Un nodo di `drawBackdrop` e' anche un contenitore che gli altri
    nodi trovano fra gli antenati (`TraversableNode`): pubblica il proprio materiale — sfondo,
    cattura, superficie, contenuto escluso — in un layer creato al primo lettore, e campiona quello
    del contenitore in cui sta, infilato nella propria pila (`Backdrop.stackedBackdrops`,
    `Backdrop.forEachPart`). Un `LayerBackdrop` sa chi lo registra e puo' essere registrato solo su
    richiesta (`onDemand`, conto dei lettori); il materiale esportato si registra di nuovo solo
    quando cambia, e la manutenzione della cattura e' separata dal suo disegno, cosi' l'export non
    la ripete. La logica sta in un file nostro, `GlassNesting.kt`, accanto ai sorgenti copiati.

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
