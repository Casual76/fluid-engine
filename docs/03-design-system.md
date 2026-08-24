# 03 · Design system

`engine-ui` è il livello visivo dell'app: non un tema Material con i colori cambiati, ma una superficie disegnata da capo sopra Material 3. Chi ci lavora deve sapere quali sono le regole, perché sono poche e sono tutte deliberate.

## Le cinque decisioni

**1. Gli angoli sono continui, non circolari.** Un angolo circolare unisce un lato dritto a un arco, e la curvatura salta da zero a `1/r` in un punto: l'occhio legge quel salto come una piega. `ContinuousCornerShape` fa salire e scendere la curvatura, senza giunzione. È metà del motivo per cui una card qui non sembra una card Material. Non usare `RoundedCornerShape` nel codice nuovo.

**2. Il carattere è Inter, con due tagli.** Testo sotto i 20sp, display sopra. Il tracking segue una curva dipendente dalla dimensione (negativo nei titoli, positivo nelle didascalie) perché Inter a tracking zero sembra largo nei titoli e stretto in piccolo. `fluidTypography()` monta tutto sugli slot di Material: si continua a chiedere `MaterialTheme.typography.titleMedium` e si ottiene la scala iOS 34/28/22/20/17/15/13/11.

**3. La palette parte da un colore solo.** Un accento genera l'intera scala di superfici, con un velo di tinta che diventa più forte solo sui contenitori piccoli e alti. Le superfici grandi restano quasi neutre. Cambiare `brand` cambia tutta l'app, coerentemente. I ruoli sono sempre coppie: un colore di sfondo non viaggia mai separato dal colore di contenuto che ci si legge sopra.

**4. Le liste sono raggruppate.** Righe dentro un unico contenitore arrotondato, separate da hairline da 0.5dp rientrate dove comincia il testo. La categoria della riga sta sulla piastrella dell'icona, mai sullo sfondo della riga: colorare le righe trasforma una lista ordinata in un patchwork.

**5. Il movimento è un vocabolario, non un effetto.** Tutto passa da `FluidMotion` (molle) e `FluidMotionScheme` (che le passa anche ai componenti Material). Una pagina in arrivo non sfuma mai: le transizioni di rotta sono opache e laterali, così non ci sono mai due pagine leggibili sovrapposte. `FluidMotionPolicy` spegne il movimento decorativo quando la scala di animazione di sistema è a zero — va rispettata, non aggirata.

## Fluid Glass: dove vive e dove no

Il vetro è **chrome**, non il materiale universale dell'app. Si usa soltanto per qualcosa che viene
disegnato sopra altro contenuto: barra superiore, tab bar/rail, azioni della barra, notifica globale,
indice laterale, sheet e alert. Hero, card, gruppi lista, campi, chip, segmentati e pulsanti dentro la
pagina restano superfici normali. Se una cosa scorre insieme alla pagina, non è vetro.

Dalla 1.4.0 l'ottica non è più nostra: la disegna la libreria `backdrop` di Kyant
([AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass), Apache-2.0), copiata come
sorgente in `ui/glass/backdrop/` — vedi `LICENSES/AndroidLiquidGlass.md`. `GlassMaterial` non
disegna niente: decide **quanto** materiale riceve ogni tipo di superficie.

Un pannello, nell'ordine in cui lo vedono i pixel:

1. **cattura** — `glassBackdropSource` registra il corpo della schermata in un `GraphicsLayer`;
   ogni pannello campiona la stessa registrazione, trasformata nelle proprie coordinate;
2. **vividezza** — la saturazione sale sopra 1: il vetro concentra il colore che trasmette, e senza
   questo passaggio un pannello sopra una foto sembra plastica grigia;
3. **sfocatura** — molto più larga di prima (16 dp di riferimento) e per questo la **tinta è molto
   più bassa**: la leggibilità si compra con il raggio, non con l'opacità;
4. **lente** — un campo di distanza della forma stessa del pannello sposta il campione sempre di più
   verso il bordo, così lo sfondo si *piega* dentro il perimetro. È il passaggio che mancava;
5. **tinta, bordo speculare, ombre** — anello illuminato da un angolo, ombra interna che dà spessore,
   ombra esterna che stacca dalla pagina.

`GlassOptics` descrive tutto questo in termini fisici: `refractionHeight` è quanto in profondità dal
bordo si sente la piega, `refractionAmount` quanto lontano trascina il campione. `GlassRole` sceglie
la ricetta: `Bar` (larga, quieta, senza ombra), `Floating` (la capsula di navigazione, trattamento
completo), `Interactive` (quasi trasparente, lente al massimo, dispersione cromatica accesa),
`Modal` (sfocatura profonda, smusso largo).

I controlli non sono più lenti dipinte: `glassControlSurface` piega davvero, si inclina verso il
dito con un `tanh`, si gonfia e si schiaccia sotto pressione e si illumina dove è stata toccata
(additivo — una riflessione speculare *aggiunge* luce; scurire al tocco è il linguaggio dei bottoni
di plastica). Un controllo che sta su una barra rifrange **la barra**, non la pagina: la barra
esporta il proprio materiale con `exports`, e chi ci sta sopra lo compone con
`rememberCombinedGlassBackdrop`. In pratica basta che la chrome fornisca `LocalGlassBackdrop`, cosa
che `FluidScreen` fa da sola.

Serve API 31 per sfocatura e vividezza, API 33 per la lente. Sotto la 31 `glassSurface` dipinge
`GlassTint.fallback`: un materiale definito e quasi opaco, non una trasparenza rotta chiamata vetro.

Vetro e resto dell'interfaccia usano finalmente **la stessa silhouette**. La vecchia eccezione — le
superfici live dovevano usare angoli circolari perché lo shader del rim non sapeva descriverne
altri — non esiste più: la lente legge i raggi da qualsiasi `CornerBasedShape`, quindi anche da
`ContinuousCornerShape`, e il rettangolo nudo di una barra a tutta larghezza è gestito come forma a
raggi zero.

Una superficie deve essere disegnata **dopo** il body che campiona. Per gli overlay appartenenti a
una schermata usare lo slot dedicato:

```kotlin
FluidScreen(
  title = "Archivio",
  overlay = { backdrop ->
    FluidSectionIndex(
      sections = sections,
      activeSectionKey = active,
      onSelectSection = onSelect,
      backdrop = backdrop,
      modifier = Modifier.align(Alignment.CenterEnd),
    )
  },
) { /* contenuto solido della LazyColumn */ }
```

Mettere un `glassSurface` dentro la `LazyColumn` registrata significa far campionare il materiale da
se stesso. Non farlo: spostare il controllo nello slot `overlay` oppure lasciarlo solido.

## Cosa c'è dentro

**Struttura di una schermata**

| | |
|---|---|
| `FluidScreen` | la schermata: titolo grande dentro lo scroll, barra di vetro che appare solo quando il titolo passa sotto |
| `FluidScreen.overlay` | chrome flottante disegnato dopo il body, con il `GlassBackdropState` corretto per quella destinazione |
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

`FluidButton` (Filled/Tinted/Plain/Destructive), `FluidSwitch`, `FluidChip`, `FluidSegmentedControl`, `FluidTextField`, `FluidColorDot`, `FluidBarAction`, `FluidGlassIconButton`, `FluidSpinner`, `FluidProgressBar`, `FluidIndeterminateBar`.

**Token**

`FluidRadius` (Small 10, Control 12, Card 18, Group 22, Sheet 38), `FluidCapsuleShape`, `FluidTextStyles` (`uppercaseCaption`, `numeric`, `largeNumeric` con cifre tabulari), `FluidTone` (Primary/Success/Warning/Danger/Info/Neutral), `GlassDefaults`, `GlassOptics` e `GlassRole` per il materiale traslucido.

## Regole per chi scrive codice nuovo

- Colori solo da `MaterialTheme.colorScheme`. Un `Color(0xFF…)` in una schermata è un colore che non seguirà l'accento, non avrà una variante scura e non passerà i controlli di contrasto. Le uniche eccezioni sono i toni fissi warning/success, che nello schema Material non hanno un ruolo.
- Testo solo da `MaterialTheme.typography` o `FluidTextStyles`. Niente `fontSize` a mano.
- Angoli da `FluidRadius` attraverso `ContinuousCornerShape`, o da `MaterialTheme.shapes` (che è già mappato su quelli).
- Animazioni da `FluidMotion`. Una `tween` scritta a mano nel mezzo di una schermata è il modo più rapido per far sembrare quella schermata di un'altra app.
- Feedback al tocco con `fluidPressable` (scala) o `fluidRowPressable` (tinta, per le righe di una lista raggruppata). Una riga che si rimpicciolisce rompe la sagoma del gruppo.
- Il contrasto è testato: `FluidContrastTest` verifica le coppie. Se aggiungi un ruolo, aggiungi il caso.

## Cosa non c'è, e perché

Niente `FeatureHero`, niente `GradePill`, niente componente che sappia cos'è una materia o un voto. Erano nell'app da cui l'engine è stato estratto e ci sono rimasti: un componente che conosce il dominio non è un componente di design system, è una schermata scritta a metà.
