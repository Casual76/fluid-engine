# 03 · Design system

`engine-ui` è il livello visivo dell'app: non un tema Material con i colori cambiati, ma una superficie disegnata da capo sopra Material 3. Chi ci lavora deve sapere quali sono le regole, perché sono poche e sono tutte deliberate.

## Le cinque decisioni

**1. Gli angoli sono continui, non circolari.** Un angolo circolare unisce un lato dritto a un arco, e la curvatura salta da zero a `1/r` in un punto: l'occhio legge quel salto come una piega. `ContinuousCornerShape` fa salire e scendere la curvatura, senza giunzione. È metà del motivo per cui una card qui non sembra una card Material. Non usare `RoundedCornerShape` nel codice nuovo.

**2. Il carattere è Inter, con due tagli.** Testo sotto i 20sp, display sopra. Il tracking segue una curva dipendente dalla dimensione (negativo nei titoli, positivo nelle didascalie) perché Inter a tracking zero sembra largo nei titoli e stretto in piccolo. `fluidTypography()` monta tutto sugli slot di Material: si continua a chiedere `MaterialTheme.typography.titleMedium` e si ottiene la scala iOS 34/28/22/20/17/15/13/11.

**3. La palette parte da un colore solo.** Un accento genera l'intera scala di superfici, con un velo di tinta che diventa più forte solo sui contenitori piccoli e alti. Le superfici grandi restano quasi neutre. Cambiare `brand` cambia tutta l'app, coerentemente. I ruoli sono sempre coppie: un colore di sfondo non viaggia mai separato dal colore di contenuto che ci si legge sopra.

**4. Le liste sono raggruppate.** Righe dentro un unico contenitore arrotondato, separate da hairline da 0.5dp rientrate dove comincia il testo. La categoria della riga sta sulla piastrella dell'icona, mai sullo sfondo della riga: colorare le righe trasforma una lista ordinata in un patchwork.

**5. Il movimento è un vocabolario, non un effetto.** Tutto passa da `FluidMotion` (molle) e `FluidMotionScheme` (che le passa anche ai componenti Material). Una pagina in arrivo non sfuma mai: le transizioni di rotta sono opache e laterali, così non ci sono mai due pagine leggibili sovrapposte. `FluidMotionPolicy` spegne il movimento decorativo quando la scala di animazione di sistema è a zero — va rispettata, non aggirata.

## Cosa c'è dentro

**Struttura di una schermata**

| | |
|---|---|
| `FluidScreen` | la schermata: titolo grande dentro lo scroll, barra di vetro che appare solo quando il titolo passa sotto |
| `FluidScreenSurface` | lo sfondo della finestra |
| `FluidTabBar`, `FluidTabRail` | navigazione principale a pillola |
| `FluidSheet`, `FluidAlert`, `FluidGrabber` | fogli modali e avvisi |
| `FluidContainerScaffold` | contenitore + dettaglio |
| `FluidSectionIndex` | indice laterale per liste lunghe |
| `FluidNotificationHost` | notifiche contestuali dentro l'app |
| `ProvideFluidChrome`, `rememberFluidChromeController` | il registro della barra e del bus "torna in cima" |

**Contenuto**

| | |
|---|---|
| `FluidListGroup`, `FluidListRow`, `FluidListDivider`, `FluidListItem` | la lista raggruppata |
| `FluidCard`, `FluidHeroCard`, `FluidEditorialCard` | superfici |
| `FluidSectionHeader`, `FluidSectionTitle`, `FluidSectionFootnote` | intestazioni e note |
| `FluidStatusBadge`, `FluidStatChip`, `FluidMetricTile`, `FluidMiniChart` | dati in piccolo |
| `FluidEmptyState`, `FluidLoading`, `FluidLoadingBlock`, `FluidInlineMessage` | stati |
| `FluidSyncIndicator`, `FluidSyncNotice`, `FluidSyncAction` | stato della sincronizzazione |

**Controlli**

`FluidButton` (Filled/Tinted/Plain/Destructive), `FluidSwitch`, `FluidChip`, `FluidSegmentedControl`, `FluidTextField`, `FluidColorDot`, `FluidBarAction`, `FluidSpinner`, `FluidProgressBar`, `FluidIndeterminateBar`.

**Token**

`FluidRadius` (Small 10, Control 12, Card 18, Group 22, Sheet 38), `FluidCapsuleShape`, `FluidTextStyles` (`uppercaseCaption`, `numeric`, `largeNumeric` con cifre tabulari), `FluidTone` (Primary/Success/Warning/Danger/Info/Neutral), `GlassDefaults` per il materiale traslucido.

## Regole per chi scrive codice nuovo

- Colori solo da `MaterialTheme.colorScheme`. Un `Color(0xFF…)` in una schermata è un colore che non seguirà l'accento, non avrà una variante scura e non passerà i controlli di contrasto. Le uniche eccezioni sono i toni fissi warning/success, che nello schema Material non hanno un ruolo.
- Testo solo da `MaterialTheme.typography` o `FluidTextStyles`. Niente `fontSize` a mano.
- Angoli da `FluidRadius` attraverso `ContinuousCornerShape`, o da `MaterialTheme.shapes` (che è già mappato su quelli).
- Animazioni da `FluidMotion`. Una `tween` scritta a mano nel mezzo di una schermata è il modo più rapido per far sembrare quella schermata di un'altra app.
- Feedback al tocco con `fluidPressable` (scala) o `fluidRowPressable` (tinta, per le righe di una lista raggruppata). Una riga che si rimpicciolisce rompe la sagoma del gruppo.
- Il contrasto è testato: `FluidContrastTest` verifica le coppie. Se aggiungi un ruolo, aggiungi il caso.

## Cosa non c'è, e perché

Niente `FeatureHero`, niente `GradePill`, niente componente che sappia cos'è una materia o un voto. Erano nell'app da cui l'engine è stato estratto e ci sono rimasti: un componente che conosce il dominio non è un componente di design system, è una schermata scritta a metà.
