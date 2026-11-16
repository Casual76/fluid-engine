<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/piano-flottante.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Piano flottante

Il **piano flottante** è il modo dichiarativo di fare le trasformazioni del vetro dalla 4.0: chi scrive l'app dice dove
sta un vetro, che identità ha e come vuole comparire; **il contenitore** (`FluidGlassContainer`) decide chi si fonde, chi
nasce da chi, chi compare per lente, e anima ogni cambio da solo. È `GlassEffectContainer` di SwiftUI, con
`glassEffect(_:in:)`, `glassEffectID`, `glassEffectUnion` e `glassEffectTransition`. Accanto ci sono il **contenuto che si
sfoca e si incrocia** (`FluidGlassContentTransition`, il `blurReplace` dentro un vetro) e le **forme concentriche**
(`FluidConcentricShape`), che servono alle barre. Tutte le firme: [api/vetro.md](../api/vetro.md) e
[api/tema.md](../api/tema.md).

**Modello mentale.** L'app scrive **solo lo stato**, con un `if`: nessuna animazione, nessun riquadro, nessun `id` oltre a
quelli che dichiara. Il contenuto è già al suo posto d'arrivo e non si stira mai; viaggia solo il vetro. Dentro il
contenitore **il vetro dei pezzi lo disegna il contenitore**, dietro al contenuto: a riposo una cattura per grappolo di
vetri vicini, durante un cambio un «palco» di Fluid-physics guidato dal piano. Il lato chiaro o scuro lo decide **una
sonda sola** per tutti i vetri, e il contenuto ne riceve l'inchiostro.

Con le altre famiglie: il materiale, le varianti e il vetro che si adatta sono in [vetro.md](vetro.md); il motore che
disegna il palco è in [fluid-physics.md](fluid-physics.md) e [fluid-physics-motore.md](fluid-physics-motore.md); le
forme e i raggi sono in [tema.md](tema.md); il movimento ridotto in [movimento.md](movimento.md). Le barre, i menu e i
fogli della 4.0 sono **clienti** del piano: lo usano, non lo riscrivono.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un tasto che diventa due, una barra che cambia azioni, un tasto che compare | `FluidGlassContainer { … if (stato) … }` con `Modifier.fluidGlassId(id, ns)` su ogni vetro; nessuna animazione |
| un vetro su un elemento qualsiasi (un'icona, un testo, un `Box`) | `Modifier.fluidGlass(glass, shape)`; in un contenitore è un pezzo del piano, fuori è un vetro a sé |
| lo spazio dei nomi degli `id` | `rememberFluidGlassNamespace()` |
| un blocco di tasti che è una cosa sola (azioni dello stesso gruppo, una capsula di comandi) | `Modifier.fluidGlassUnion(id, ns)` su ognuno, **prima** del vetro: una sagoma sola anche ferma (sotto, «L'unione») |
| un vetro che compare sempre per lente, anche vicino a un altro | `Modifier.fluidGlassTransition(FluidGlassTransition.Materialize)` |
| un vetro che c'è o non c'è, senza transizione | `FluidGlassTransition.Identity` |
| un cambio senza viaggio, o con la mia molla | `FluidGlassContainer(animation = FluidGlassAnimation.None)` / `FluidGlassAnimation.Custom(spec)` |
| le icone di una barra che cambiano contesto | una `FluidGlassContentTransition` **per posto**, con la chiave del contenuto |
| un angolo che segue quello del contenitore | `FluidConcentricShape(minimum)` sul pezzo, `Modifier.fluidContainerShape(forma)` sul contenitore |
| gli stessi raggi per chi disegna da sé | `rememberFluidConcentricRadii(minimum)` + `Modifier.fluidConcentricRadii(state)` |
| un vetro solo che gira chiaro/scuro col suo contenuto | un `FluidGlassContainer` con **un pezzo solo** (il patto dell'inchiostro) |
| il limpido sopra una foto | `Modifier.fluidGlass(FluidGlass.Clear)` (sotto, «`Clear`») |
| capire che cosa ha deciso il piano | `FluidGlassContainerDiagnostics` (viaggi, ripartenze, avvisi d'ordine, ultimo viaggio, ultimo riposo) |
| vedere tutto questo su un dispositivo | la pagina «Piano flottante» della galleria (`--es gallery.page plane`) |
| un contenitore attorno a una lista che scorre | **non così**: il contenitore va attorno alla barra, non alla lista (sotto, «Quando usarlo») |
| `fluidGlassId` scritto dopo il vetro | **non così**: non ha effetto, e in debug il log `FluidGlass` lo dice (sotto, «L'ordine è contratto») |

## Quando usarlo, e quando no

**Sì**, attorno a un gruppo di vetri che cambia insieme: una barra di tasti, la capsula in basso con la sua ricerca, un
tasto che diventa due, i tasti di un lettore, il tasto che si accende accanto a un altro. È la forma di serie di ogni
trasformazione fra vetri vicini dalla 4.0.

**Anche** attorno a un vetro solo, quando il suo contenuto deve girare chiaro/scuro con lui (il patto dell'inchiostro,
sotto): un contenitore con un pezzo.

**No** attorno a una lista o a un contenuto che scorre: per il piano ogni fotogramma di scorrimento è un cambio di
posizione dei pezzi, cioè un viaggio. Un contenitore che si sposta **tutto insieme** (la barra che scorre via) invece non
cambia niente: le coordinate dei pezzi sono le sue. **No** per un vetro che deve stare dentro un altro vetro dello stesso
contenitore (i pezzi sono fratelli, sotto). **No** per le superfici che hanno già il loro motore (menu che si
trasforma, foglio, pop-up): sono clienti del piano nelle fasi che li portano.

## Le firme

| SwiftUI | Fluid |
|---|---|
| `glassEffect(_:in:)` | `Modifier.fluidGlass(glass = FluidGlass.Regular, shape = FluidCapsuleShape, backdrop = currentGlassBackdrop())` |
| `GlassEffectContainer(spacing:)` | `FluidGlassContainer(modifier, spacing = FluidGlassContainerDefaults.Spacing, animation = FluidGlassAnimation.Default) { }` |
| `@Namespace` | `rememberFluidGlassNamespace()` → `FluidGlassNamespace` |
| `glassEffectID(_:in:)` | `Modifier.fluidGlassId(id, namespace)` |
| `glassEffectUnion(id:namespace:)` | `Modifier.fluidGlassUnion(id, namespace)` |
| `glassEffectTransition(_:)` | `Modifier.fluidGlassTransition(FluidGlassTransition.MatchedGeometry / Materialize / Identity)` |
| `withAnimation` | niente: il contenitore anima da solo; `FluidGlassAnimation.Default / None / Custom(spec)` |
| `blurReplace` dentro un vetro | `FluidGlassContentTransition(targetState, key = …) { stato -> … }` |
| `ConcentricRectangle`, `containerShape(_:)` | `FluidConcentricShape(minimum)`, `Modifier.fluidContainerShape(shape)` |

Il caso tipo (è la scena `uno-due` della galleria):

```kotlin
val ns = rememberFluidGlassNamespace()
FluidGlassContainer {
  Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
    FluidGlassIconButton(onClick = edit, modifier = Modifier.fluidGlassId("edit", ns)) { FluidIcon(FluidIcons.Edit, null) }
    if (editing) {
      Box(Modifier.fluidGlassId("done", ns).fluidGlass().height(44.dp).padding(horizontal = 20.dp)) { Text("Fatto") }
    }
  }
}
```

Un `if` e nessuna animazione: «done» nasce da «edit» se gli sta entro lo `spacing`, altrimenti compare per lente. I tasti
di vetro dell'engine (`FluidGlassIconButton`, `FluidGlassButton`, ogni `glassControlSurface`) dentro un contenitore sono
pezzi come gli altri; `FluidGlassIconButton` inoltra al suo vetro gli attributi scritti sul suo `modifier`.

Il contenuto del contenitore è posato come in un `Box` (in alto all'inizio, il contenitore grande quanto il più grande
dei figli): di solito è una `Row` o una `Column`.

## L'ordine è contratto

**`fluidGlassId`, `fluidGlassUnion` e `fluidGlassTransition` valgono per il `fluidGlass` (o il tasto di vetro dell'engine)
alla loro destra nella stessa catena, e per nessun altro**: né i figli, né un vetro più a sinistra. Scritti dopo non
hanno effetto, e in una build di debug il log `FluidGlass` dice «… non ha effetto: va scritto prima del fluidGlass …».

```kotlin
Modifier.fluidGlassId("play", ns).fluidGlass()   // giusto: il vetro è "play"
Modifier.fluidGlass().fluidGlassId("play", ns)   // l'id non conta, avviso in debug
```

Il perché: è la regola dei modificatori di Compose (ciò che sta a sinistra avvolge ciò che sta a destra), e un vetro
con due nomi possibili, a seconda di dove si scrive l'attributo, è un vetro che si accoppia male in silenzio. Lo provano
`FluidGlassContainerOrderTest` (engine) e `FluidGlassContainerTest` (galleria); il contatore è
`FluidGlassContainerDiagnostics.totalOrderWarnings`.

## Gli `id` e lo `spacing`

**Date gli `id`.** Lo stesso `id` prima e dopo è **lo stesso vetro**, che si sposta e cambia forma. Senza `id` i vetri si
accoppiano per lato e per posizione (come la barra di prima), e quando ne sparisce uno in mezzo possono sbagliare: un
vetro prende il posto del vicino e la barra «scivola». Due vetri con lo stesso `id` nello stesso contenitore: vale il
primo, l'altro è anonimo. Gli `id` valgono nel loro spazio dei nomi (`rememberFluidGlassNamespace`): lo stesso `id` in
due spazi è due vetri.

**Lo `spacing`** (`FluidGlassContainerDefaults.Spacing`, **16 dp**) dice tre cose insieme:

- fin dove due vetri sono **un grappolo**: una cattura sola a riposo, un campo fuso in viaggio (a riposo i pezzi
  **distinti** di un grappolo restano separati del tutto, scelta di Alessio del 2026-11-11: il collo esiste solo in
  viaggio);
- fin dove un vetro nuovo **nasce dal vicino** invece di comparire per lente;
- il raggio di fusione del collo in viaggio: fino a uno `spacing` di varco il collo è intero, a una volta e mezza si
  rompe, al doppio non arriva.

**16 dp è la scelta di Alessio del 2026-11-16** (tavola «distanza di fusione» di F3.3: stretta 8 / media 16 / larga 24,
fusi già a riposo): copre i due vetri di un gruppo (8 dp, `FluidMetrics.GlassSpacing`) e due gruppi vicini (12 dp,
`FluidMetrics.GroupSpacing`) con un margine, perché l'arrotondamento al pixel non decida chi si fonde. Apple non la
documenta (i suoi esempi usano 20 e 40 pt). La pagina «Piano flottante» riprende le tre distanze della tavola con
`plane.fusion stretta|media|larga` (la manopola `FluidGlassContainerDiagnostics.fusionPreview`, solo per le prove).

## L'unione: un blocco di tasti è una cosa sola

**Quando più tasti formano un blocco, dichiaratelo con `fluidGlassUnion`.** A riposo due vetri distinti, anche a 8 dp,
restano **separati del tutto** (tre vetri vicini sono tre vetri); quelli della stessa unione sono **una sagoma sola**,
ferma o in viaggio: una goccia col suo collo pieno fra un tasto e l'altro, come la capsula di comandi di Apple. È la
fusione a riposo che Alessio ha voluto riservare a chi la dichiara (2026-11-16: «con incitamento agli utilizzatori
dell'engine a sfruttare l'unione in blocchi di tasti quando appropriato»).

```kotlin
val ns = rememberFluidGlassNamespace()
FluidGlassContainer {
  // Tre azioni dello stesso gruppo: una capsula di comandi.
  Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
    for (action in listOf(Bold, Italic, Underline)) {
      FluidGlassIconButton(
        onClick = { apply(action) },
        modifier = Modifier.fluidGlassId(action, ns).fluidGlassUnion("formato", ns),
      ) { FluidIcon(action.icon, action.label) }
    }
  }
}
```

**Sì**: azioni dello stesso gruppo che si leggono insieme (formato del testo, i comandi di un lettore, «indietro / avanti»
di una pagina, una capsula con più icone), un controllo segmentato fatto di vetri, i tasti di una barra che stanno sempre
insieme. **No**: tasti di **significato diverso** accostati per caso (un «Cerca» accanto a un «Condividi» non sono una
cosa sola: fusi, sembrano un comando unico); **gruppi lontani** (oltre circa un raggio e mezzo d'angolo di varco, 30 dp
per tasti da 44, il collo pieno non sta più nel filo dei tasti: l'unione resta una cattura sola con sagome separate, e non
serve); un vetro che **compare o scompare per lente** (mentre compare è un campo a sé, e si unisce agli altri solo a
riposo); due **gruppi diversi** della stessa barra (12 dp, `FluidMetrics.GroupSpacing`): ognuno la sua unione, o
nessuna. Un'unione resta un grappolo solo fino a otto pezzi; `fluidGlassUnion` va **prima** del vetro, come `fluidGlassId`.

## Quando lo stato cambia

Il piano confronta lo stato di prima e quello di adesso, una volta per cambio:

| prima → adesso | che cosa fa | quanto dura |
|---|---|---|
| stesso `id` | **lo stesso vetro** si sposta e cambia forma; i vicini che si avvicinano si fondono passando per un collo, quelli che si allontanano si strozzano e si dividono | molla di serie, ferma a vista a `FluidMotion.PlaneContextMillis` (300 ms) |
| `id` nuovo entro `spacing` da un vetro di prima | **nasce da lui**: parte sovrapposto e se ne stacca col collo | come sopra |
| `id` nuovo più lontano (o `Materialize`) | **compare per lente** al suo posto: la rifrazione sale, il contorno si stringe da `FluidMotion.PlaneAppearScale` (×1,09); il corpo non sfuma mai | `FluidMotion.PlaneAppearMillis` (430 ms) |
| un vetro che se ne va vicino a un altro | **vi confluisce** | 300 ms |
| un vetro che se ne va lontano | **scompare per lente** | `FluidMotion.PlaneDisappearMillis` (470 ms) |
| `fluidGlassUnion` uguale | **un grappolo solo e una sagoma sola**, anche oltre lo `spacing` (mai oltre otto pezzi), ferma o in viaggio | — |
| `Identity` | c'è o non c'è | nessuna |

**I tempi sono quelli a vista** (F3.2c): la sagoma entra per sempre nello 0,5% della corsa al tempo della tabella (la
capsula della traccia `contesto-capsule-curva` a ~294 ms, contro i 270–330 ms di Apple), e il viaggio finisce poco dopo,
quando ogni lato è a mezzo pixel dal suo arrivo. Fino a F3.2 la molla si stirava sulla coda numerica e la sagoma si
fermava a vista a 133 ms. **Il collo** si strozza una volta sola, «spesso» (esponente 1/2, scelta di Alessio del
2026-11-16), e si rompe dopo che i pezzi si sono staccati, a circa tre quarti del moto (~228 ms, ~70 ms prima del
riposo a vista: l'ordine delle fasi di R4 B2 (d), dove Apple lo rompe all'80% del tempo). Il suo orologio dura il doppio
del riposo a vista, così il collo lungo scelto da Alessio cade nella stessa fase della molla in cui è stato tarato. Durante il viaggio il vetro
**schiarisce** (il vetro in viaggio, dalla velocità dei suoi lati) e torna smerigliato negli ultimi istanti.

**Un cambio a metà viaggio riparte da dove i vetri si trovano**, con la velocità che hanno, e il collo riparte da quello
che c'era (invariante 16): niente torna indietro di colpo, niente compare due volte, e il viaggio non va in coda. Con
`FluidGlassAnimation.Custom(spec)` la molla dell'app conserva la velocità, una curva a durata no (riparte da ferma).

**La comparsa per lente** (R4 B3): prima il contorno, già più largo; poi la lente, con la sfocatura che cresce con lei
(mai un'immagine doppia); il **simbolo** (il contenuto del pezzo, qualunque sia) arriva dilatato 2× e traslucido e si
condensa per ultimo. Chi compare o scompare è sempre un campo a sé, quindi la comparsa è la stessa accanto a un vetro
che si sposta. Nella scomparsa il vetro svanisce per lente; il contenuto, che l'app ha già tolto, non c'è più.

**Movimento ridotto**, `FluidGlassAnimation.None` o scala delle animazioni di sistema a zero: nessun viaggio, i vetri vanno
al posto nuovo nel fotogramma dopo il cambio. La scala di sistema allunga i viaggi (è il «lento» delle riprese).

## Il passaggio di consegne: riposo e palco

Per ogni vetro, in ogni fotogramma, c'è **una superficie sola** che lo disegna:

| fotogramma | chi disegna |
|---|---|
| il fotogramma del cambio | il **riposo di prima** (il contenuto è già al posto nuovo): è il primo fotogramma del viaggio, che parte esattamente da lì |
| dal fotogramma dopo, per tutto il viaggio | il **palco**: una superficie di Fluid-physics per campo del viaggio, grande quanto il palco più il margine dell'ombra, guidata dal piano «a fotogramma» (`driveByFrame`: la forma di ogni fotogramma col suo collo, le unioni, la comparsa e il vetro in viaggio). I grappoli d'arrivo sono già montati e spenti (niente disegno, niente cattura) |
| l'ultimo fotogramma | i **grappoli d'arrivo** riprendono il vetro nella stessa composizione in cui il palco si smonta, dalla geometria d'arrivo (entro mezzo pixel) |

Chi porta il vetro si decide in composizione, una volta per tutte le superfici (la regola del passaggio di consegne in
[../regole.md](../regole.md)). Riposo e palco usano la stessa ricetta e lo stesso corpo: col materiale nuovo il passaggio
non si vede, purché sia uno. Il criterio sul dispositivo è F3-b: nei primi e negli ultimi cinque fotogrammi del viaggio
nessun salto.

## Il contenuto che si sfoca e si incrocia

`FluidGlassContentTransition(targetState, modifier, key, blurReference, contentColor, contentAlignment) { stato -> … }`:
chi esce si sfoca, rimpicciolisce appena e svanisce; chi entra arriva sfocato e si fa nitido; i due si incrociano al
massimo della sfocatura, e la tinta cambia lì dentro. **Il contenuto con la stessa `key` nei due stati non si tocca**:
resta nitido anche se il vetro intorno corre.

- **Una transizione per posto**: in una barra di tre icone, tre transizioni. Una sola per tutta la barra sfocherebbe anche
  l'icona che resta.
- `key` è l'identità del contenuto (il simbolo, il testo); `null` è il vuoto (niente da disegnare, e il contenuto dopo il
  vuoto nasce da scala 0,75).
- `blurReference` è la sagoma su cui si misura la sfocatura: per un'icona la sua taglia (26 dp in una barra,
  `FluidMetrics.BarIconSize`), per un testo l'altezza di riga. La sfocatura massima è 0,15 della sagoma (≈2,9 dp su
  un'icona da 26 dp), a metà del tempo; il tempo di serie è 400 ms.
- `contentColor`, se c'è, dà il colore di ogni stato e lo incrocia dentro la sfocatura: il colore arriva prima della forma.
- Sotto Android 12 (API 31) non c'è `RenderEffect`: il cambio è una dissolvenza senza sovrapposizione. Con il movimento
  ridotto il contenuto cambia di colpo. La scala delle animazioni di sistema allunga anche questo.
- La forma del vetro non è affare suo: la cambia il contenitore. Il contenuto sta già al suo posto d'arrivo.

## Le forme concentriche

`FluidConcentricShape(minimum)`: raggio = raggio del contenitore − distanza dal suo bordo, **angolo per angolo**, mai sotto
`minimum` né oltre mezzo lato corto. La distanza di un angolo è la **maggiore** delle due (dal bordo verticale e da
quello orizzontale): con margini diversi è l'unica che tiene l'angolo interno dentro quello esterno. Il contenitore di
forma si dichiara con `Modifier.fluidContainerShape(shape)` (un `ModifierLocal`): il riquadro è quello che vedono i
modificatori **alla sua destra**, quindi in `Modifier.fluidContainerShape(s).padding(8.dp)` il contenuto sta a 8 dp dal
bordo e un suo angolo concentrico prende il raggio di `s` meno 8.

- **Dentro un `FluidGlassContainer`** il piano riceve la forma già risolta: per il campo una concentrica è una forma ad
  angoli tondi come le altre, e un cambio di margine viaggia coi raggi (scena `concentriche` della galleria).
- **Fuori da un contenitore del piano** i modificatori dell'engine la risolvono al layout: **il primo fotogramma vede il
  minimo**.
- Da sola, senza contenitore di forma, è una `ContinuousCornerShape` di raggio `minimum`.
- Chi disegna da sé: `val radii = rememberFluidConcentricRadii(minimum)` e `Modifier.fluidConcentricRadii(radii)` sul nodo
  da misurare; `radii.radii` sono i quattro raggi in px.

Le misure che servono alle barre sono in `FluidMetrics`: 8 dp fra due vetri (`GlassSpacing`), 12 fra due gruppi
(`GroupSpacing`), passo di 44 dp delle icone dentro un gruppo (`GroupItemPitch`), margine della capsula dai bordi
(`CapsuleSideMargin`, `CapsuleBottomMargin`), bersaglio di tocco 48 dp (`TouchTarget`).

## Il lato e l'inchiostro: il patto delle app

**Adattamento uniforme.** Lato e inchiostro li decide il contenitore, con **una sonda sola** sul riquadro del grappolo più
grande: i pezzi non sondano e girano insieme, nello stesso fotogramma. Una barra di tre vetri sopra una foto che cambia
gira tutta insieme (scena `adattamento`). Un grappolo più grande di un tasto (lato corto oltre la soglia dei grandi)
scurisce in continuo invece di girare, come i grandi di [vetro.md](vetro.md). Un contenitore dentro un vetro adattivo (un
foglio, un pop-up) non sonda: prende il lato da lui.

**Il patto dell'inchiostro è il contenitore.** Il contenuto dentro un `FluidGlassContainer` riceve l'inchiostro del lato
(`LocalContentColor`, lo schema di colori del lato e `LocalFluidGlassAppearance`) e al giro svanisce e risale con lui.
**Un `fluidGlass` da solo, fuori da un contenitore, non gira** (come `glassSurface`): il vetro non sa di che colore è
ciò che l'app gli disegna sopra, e un vetro che girasse sotto un testo scuro lo renderebbe illeggibile. **Per far girare
un vetro solo basta un contenitore con un pezzo.** Chi colora a mano (`tint = …`) legge `LocalFluidGlassAppearance`.

I tasti di vetro dell'engine dentro un contenitore prendono il lato del contenitore anche col loro inchiostro (il
difetto del «tasto annidato del lato del tema» è chiuso qui, per i tasti dentro un contenitore).

## `Clear`

`Modifier.fluidGlass(FluidGlass.Clear)` è il limpido: niente smerigliatura, uno scurimento fisso del 35% sotto il vetro
(HIG), nessuna adattività. Va usato solo se tutte e tre: sta sopra contenuto ricco (foto, video), quel contenuto regge lo
scurimento, e ciò che sta sul vetro è chiaro e marcato.

Dalla F3.2c è **la variante della ricetta** fino alle superfici (lastra ferma, superficie fisica del riposo e del palco),
dentro e fuori da un contenitore: lo sfondo arriva nitido per la mescola a monte, **col raggio di sfocatura che resta**
(piano 3.6), quindi fra limpido e di serie cambia un peso e la cattura non si rifà. Col vetro di prima e sotto Android 13
il limpido è il vetro di serie. `Clear` e `Regular` nello stesso contenitore non sono mai un grappolo (varianti diverse).

## Cosa non fa ancora

- **Fratelli, non annidati**: un vetro dentro un altro pezzo dello stesso contenitore non ne campiona il materiale (niente
  vetro su vetro fra pezzi). Il contenitore dentro un pannello di vetro sì, campiona il pannello (scena `concentriche`).
- **Forme**: una forma che il campo non sa rendere (un `Outline` generico) resta un vetro a sé, fuori dal piano.
- **L'unione lontana**: oltre circa un raggio e mezzo d'angolo di varco il collo pieno non sta nel filo dei tasti, e
  l'unione resta una cattura sola con sagome separate. Apple a quella distanza disegna probabilmente una capsula sola
  (l'inviluppo): da vedere in S3.
- **Crescita sotto il dito**: c'è nelle lastre di un pezzo solo; in un grappolo di più pezzi la lastra non cresce (il
  contenuto sì): la porta nella geometria la fase dei controlli (F6.1). `depth`, `frozen`, `heldWhileMoving` di
  `glassControlSurface` li decide il contenitore.
- **Chi compare accanto a chi si sposta** è un campo a sé (una superficie in più per la durata della comparsa): la sua
  comparsa è esatta, ma mentre compare non si fonde col vicino.
- **Il simbolo di chi scompare**: il contenuto di un vetro tolto dall'app se ne va con lui, subito; il vetro scompare per
  lente da solo.
- **Il rientro iniziale** di una capsula che si allarga (R4 B2 (c): il bordo rientra di 0,19 H prima di allargarsi) non
  c'è: le molle per lato partono ferme e non anticipano.
- **Altri componenti** che posano il `modifier` dell'app fuori dal vetro non inoltrano ancora gli attributi del piano:
  `FluidGlassMenuButton`, il tasto di `FluidMorphMenuButton` e i controlli di `FluidControls.kt`. Lì un `fluidGlassId` non
  ha effetto e in debug il log lo dice; arrivano con barre e menu (F4A, F5A).
- **Vetro di prima** (`FluidGlassModel.Classic`) e **sotto Android 13**: il contenitore non disegna; ogni pezzo è il vetro
  di sempre, senza viaggi.
- **Più di otto pezzi** in un grappolo: il grappolo si divide, dichiarato.
- **Un pannello col suo ruolo**: un pezzo si disegna come `fluidGlass` (ruolo `Floating`, tinta dei controlli) o come
  `glassControlSurface` (ruolo `Interactive`); un `glassSurface` `Modal` con la sua tinta, o un pannello che esporta il suo
  materiale ai tasti sopra di lui, non ha ancora un pezzo che lo renda uguale. Per questo card, dock, barra di scrittura e
  composer di `engine-ai-ui` restano del lato del tema, e solo i loro pezzi piccoli, da soli, girano col contenitore
  (F3.2b, [../moduli/ai-ui-overlay.md](../moduli/ai-ui-overlay.md), «Il vetro e il lato»).
- Le tre tavole di gusto di F3.3 sono decise (Alessio, 2026-11-16): distanza di fusione media (16 dp), collo «spesso»
  (1/2), comparsa da 1,09. Il collo dei viaggi di `morphTo` (menu, barre) resta a 2/3, com'era.

## Costi noti

- **A riposo**: una cattura per grappolo (un pezzo solo è la lastra di sempre, con l'ombra a maschere; due o più sono
  una superficie fisica ferma con l'ombra in corpo, il nodo allargato fin dove arriva l'ombra) e **una sonda** per
  contenitore. Un contenitore fermo non chiede fotogrammi: il suo orologio dorme finché niente cambia.
- **In viaggio**: una superficie fisica per campo del viaggio, per tutta la durata (il riposo a vista più la coda sotto
  il mezzo pixel: ~440 ms per una capsula che si stringe di 336 px), guidata «a fotogramma»: la guida si installa una
  volta, ogni fotogramma è una forma nuova (una piccola allocazione) e il piano si rifà solo quando la forma cambia.
  Una comparsa accanto a un vetro che si sposta è una superficie in più.
- **Il contenuto che si sfoca**: un `RenderEffect` di sfocatura per strato, solo durante il cambio.
- **Da misurare sul dispositivo** (sessione S3): il costo di un palco con 4 e con 8 pezzi (scena `molti` della galleria:
  gli otto sono uniti, quindi un campo solo pieno), e un viaggio sopra un contenuto che scorre (la cattura del palco va
  rifatta a ogni fotogramma).

## La diagnostica

`FluidGlassContainerDiagnostics` (per le prove e le sessioni, non per le app): `totalJourneys` (viaggi partiti),
`totalRetargets` (ripartiti a metà), `totalOrderWarnings`, `liveContainers`, `lastJourney` (un
`FluidGlassFlightRecord` per vetro in volo: da quale `id` a quale, il cambio, il vicino da cui nasce o in cui confluisce)
e `lastRest` (un `FluidGlassPieceRecord` per vetro a riposo: `id`, riquadro, raggi risolti, taglia del grappolo, unione). Le
sonde vive: `FluidGlassProbeDiagnostics.liveProbes`. La pagina «Piano flottante» della galleria scrive tutto questo in
logcat (`adb logcat -s FluidPiano`), una riga per cambio di stato e per fine viaggio.

## Cosa è verificato dai test

- `GlassPlaneTest`: la logica pura del piano (registro, grappoli, accoppiamento, classificazione, palco, viaggio
  interrompibile, riposo a vista dentro R4 B2, comparse in un campo a sé). `PhysicsPlaneNeckTest`: il collo del piano
  (una rottura sola ~186 ms prima del riposo, la riunione che si vede, l'esponente «spesso», l'unione che è una sagoma
  sola a riposo e i distinti separati).
- `FluidGlassContainerDriverTest`, `FluidGlassContainerOrderTest`, `FluidGlassContainerProbeTest`,
  `FluidGlassContainerLookTest`: il conducente, l'ordine dei modificatori, la sonda unica, l'aspetto dei pezzi.
- `FluidGlassContentTransitionTest` (il contenuto che si sfoca), `FluidConcentricShapeTest` (l'aritmetica dei raggi),
  `FluidMetricsTest`.
- Nella galleria, in Robolectric: `FluidGlassContainerTest` (i pezzi si accoppiano come dichiarato, l'ordine, una cattura
  per grappolo e una sonda, il tasto dell'engine come pezzo, la concentrica nell'albero) e `PlanePageTest` (ogni scena
  della pagina «Piano flottante»: il viaggio dichiarato, nessun avviso d'ordine, il fondo che cambia non apre viaggi,
  il comando che interrompe fa un viaggio e due ripartenze).
- **Non verificato dai test**: come si vede. Il collo, la comparsa, il passaggio di consegne, il giro uniforme e il
  contenuto sfocato si guardano sul tablet (sessione S3, elenco in `artifacts/liquid-glass/sessioni/f3-6/riprese-S3.md`)
  e sul banco (`tools/glassbench`, scene `f3/`).

## Dove guardare

- `engine-ui/…/ui/fluid/FluidGlassContainer.kt`: contenitore, modificatori e loro ordine, conducente, palco e riposo,
  sonda unica, diagnostica. `…/ui/fluid/FluidGlass.kt`: `FluidGlass` e `Modifier.fluidGlass`.
- `…/ui/glass/field/GlassPlane.kt`: la logica pura (registro, grappoli, accoppiamento, viaggio); il contratto delle tracce
  in `tools/glassbench/traces/f3/README.md`.
- `…/ui/fluid/FluidGlassContentTransition.kt`, `…/ui/fluid/FluidConcentricShape.kt`, `…/ui/fluid/FluidMetrics.kt`, i token
  `Plane*` in `…/ui/fluid/FluidMotion.kt`.
- La galleria: `sample/…/plane/` (pagina «Piano flottante», con il suo `LEGGIMI.md`).
