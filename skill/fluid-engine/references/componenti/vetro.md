# Vetro

Il «Fluid Glass» è il materiale traslucido dell'engine. Barre, tab bar, modali, menu, card e tasti non sono
rettangoli semitrasparenti: sono superfici che **rifrangono** quello che hanno dietro — una sfocatura
piccola, una lente che piega l'immagine verso il perimetro, una tinta, un bordo speculare, un'ombra. Questa
scheda è la parte «materiale» della famiglia: come si registra cosa sta dietro il vetro, che **ruolo** ha un
pannello e cosa riceve ogni ruolo, come si compone l'ottica e la tinta, quanto costa e come degrada. La
libreria che disegna davvero (`drawBackdrop`, gli effetti, il bordo, le ombre, i gesti; copiata da Kyant0,
Apache-2.0) è in [vetro-backdrop.md](vetro-backdrop.md). Tutte le firme: [api/vetro.md](../api/vetro.md).

Il modello mentale, in due frasi. **Una sorgente** registra in un `GraphicsLayer` il contenuto che il vetro
deve mostrare; **ogni pannello** (`glassSurface`) rigioca quella registrazione nelle proprie coordinate,
la passa in una catena di `RenderEffect` (vividezza, sfocatura, lente) e ci dipinge sopra tinta, bordo e
ombra. L'engine non ha opinioni sull'ottica in sé (la fa la libreria) ma su **quanta** ne riceve ogni tipo
di superficie: quella è `GlassDefaults`, ed è l'unica cosa che si ritocca.

Le superfici pronte (`FluidScreen`, `FluidFloatingTabBar`, `FluidCard(glass = true)`, i modali, i menu) usano
già tutto questo: un'app che le usa non scrive mai `glassSurface`. Questa scheda serve a chi scrive un
pannello nuovo, a chi deve capire perché un pannello si vede in un modo, a chi deve tenere sotto controllo
il costo.

**Dalla 4.0 i materiali sono due.** Da Android 13 il vetro è disegnato di serie dal materiale nuovo, **`Liquid`**,
che porta il vetro verso quello di iOS 27: le lastre di `glassSurface` e `glassControlSurface`, le superfici di
Fluid-physics ferme e in viaggio (menu, popover, finestra che si espande, barra fra le pagine), la pillola della tab
bar e la goccia dei controlli e dei menu. **È un materiale solo**: tutte queste strade passano dalla stessa ricetta,
e chi ha bisogno di qualcosa di diverso (la pillola un po' più scura, la goccia limpida) lo deriva dal profilo di
serie senza numeri suoi. Il vetro di prima, **`Classic`**, resta com'era sotto Android 13, sulla fascia in alto dove
il bordo dello scorrimento non c'è (vedi [schermata.md](schermata.md)), sullo scrim dei
modali e dove un'app lo chiede. Da Android 13 il vetro nuovo **si adatta a ciò che ha dietro** (sezione «Il vetro che si
adatta»). Il vetro **dipinto** di fogli e avvisi in un'altra finestra è
derivato dalla stessa ricetta (sezione «Il vetro dipinto»). La sezione che segue, «Due materiali», dice chi sceglie,
cosa cambia per un'app che aggiorna e com'è fatto il materiale nuovo. Il resto della scheda vale per tutti e due
dove parla di API (sorgenti, fondali, `glassSurface`, ruoli, tinte, qualità, vetro su vetro, costo), e descrive il
vetro di prima dove dà numeri di ottica: la catena qui sotto, la tabella dei ruoli, l'ottica, la regola della taglia
del vetro di prima e le alfa delle pellicole.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| una pagina con barra e card di vetro | `FluidScreen` (registra corpo e canvas da solo, fornisce `LocalGlassBackdrop` alla chrome). Non scrivere `glassSurface` |
| una card o un gruppo lista di vetro | `FluidCard(glass = true)`, `FluidListGroup(glass = true)`: sul **contenitore**, mai sulla riga. Serve un canvas ambientale (`FluidScreen(ambient = …)` o `FluidAmbientSurface`); senza, disegnano l'opaco di sempre |
| un mio pannello di vetro | `rememberGlassBackdrop()` + `Modifier.glassBackdropSource(state)` sul contenuto da rifrangere, poi `Modifier.glassSurface(state, tint, shape, role = …)` su un fratello **disegnato dopo** |
| un pannello flottante nuovo (popover, callout, foglio…) | `GlassDefaults.panelOptics(compact, overSharpPage)` + `GlassDefaults.panelTint(colour)`. Mai sfocatura, lente o bordo propri |
| la tinta giusta per una barra, una capsula, un controllo, una card, un modale | `GlassDefaults.barTint()`, `floatingTint()`, `controlTint()`, `contentTint()`, `modalTint()`: tabella sotto. **Seguono il contrasto di sistema da sole** |
| la barra sopra una fotografia | `barTintOnPhoto()` e `floatingTintOnPhoto()`, **insieme o per niente** |
| un modale che porta il colore della cosa da cui nasce | `GlassDefaults.tintedModalTint(colour)` (o `panelTint(colour)`) |
| un tono mio su una lastra, che resti com'è col materiale nuovo | `GlassDefaults.barTint().copy(overlay = colore)` (o una `GlassTint` a tre argomenti): un colore cambiato si dipinge com'è. Sezione «Due materiali», «Le tinte» |
| il vetro di prima su una schermata, o su tutta l'app | `CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Classic) { … }` attorno ad essa (o alla radice) |
| sapere quale vetro c'è in scena e perché | `FluidGlassDiagnostics.liveSurfaces` e `liveModelChoices`, con `enabled` acceso |
| sapere se il dispositivo compila gli shader del vetro | `FluidGlassDiagnostics.shaderVerdicts`, `compileAllShaders()`; logcat `-s FluidGlass` |
| vedere i ripieghi di Android 12 e 11 su un dispositivo nuovo | `FluidGlassDiagnostics.simulateApiLevel(context, 32 \| 30)`, solo in una build di debug |
| un tasto o un cursore su un pannello di vetro | niente: dalla 2.13.0 il vetro dentro un altro vetro ne campiona il materiale da solo |
| un pannello disegnato con **un altro motore** che deve ospitare vetro dell'engine | `glassMaterialSource(state)` sul nodo che porta solo il materiale + `glassContainer(state)` sul contenuto |
| un contenuto che **non è figlio** del vetro su cui sta (finestra che si trasforma) | `exports = state` sulla superficie, `glassContainer(state)` sul contenuto |
| dire all'engine da che parte sta l'app (chiaro/scuro) quando la palette mente | `LocalFluidSurfaceSide` (`FluidTheme` lo fornisce già) |
| sapere se la risposta del vetro è «intera» ora | `currentGlassQuality()` |
| un pannello sopra uno sfondo che si muove, troppo caro | `resampleIntervalMillis`, `frozen`, `heldWhileMoving` (sezione «Il costo») |
| vetro su ogni riga di una lista | **non così**: un `FluidListGroup` è un nodo, non dodici |
| un'ombra sotto il vetro | **non così** `Modifier.shadow`: trapela dentro la sagoma. Il vetro disegna la propria; `glassShadow` (interna) per un antenato che ritaglia |
| una goccia (pomello, pillola) con `GlassRole.Interactive` | **non così**: la goccia ha il suo vetro (`glassDrop` in `glass/interaction/GlassDrop.kt`, col materiale nuovo il profilo `GlassDropProfile`), vedi [vetro-backdrop.md](vetro-backdrop.md) |
| vedere le strade del vetro una accanto all'altra | la pagina «Parità» della galleria (`--es gallery.page parity`) |
| scalare un nodo di vetro per farlo «rimbalzare» | **non così**: il layer scalato rifrange ciò che non c'è più. La trasformazione sta nel `layerBlock` di `glassSurface` |

## Due materiali: `Liquid` e `Classic` (4.0)

**`Liquid`** è il materiale nuovo, del progetto che porta l'engine verso il Liquid Glass di iOS 27: corpo, luce e
ombra escono da un campo di distanza della sagoma, con shader dell'engine (`ui/glass/field/`). **`Classic`** è il
vetro della 3.x, la lente di Kyant con la pellicola dipinta, l'anello speculare e un'ombra: quello che il resto di
questa scheda descrive con i suoi numeri. Sono due perché il materiale nuovo non può esistere ovunque (chiede AGSL,
cioè Android 13, e un compilatore che accetti le sue sorgenti), e dove non può il vetro deve essere quello di prima
**com'era**, non una copia impoverita del nuovo. Il codice di `Classic` è rimasto dov'era, dietro un `if` in
`glassSurface`, e le sue immagini di riferimento del banco (`golden/classic`) sono rimaste identiche al pixel dopo
l'innesto del materiale nuovo.

**Cosa non cambia nel codice, e cosa sì.** Nessuna firma sparisce e le chiamate esistenti compilano senza modifiche:
`glassSurface`, `glassControlSurface`, `GlassOptics`, `GlassDefaults` e `GlassRole` si chiamano come prima. Si aggiungono
soltanto parametri in coda con un valore di serie (`GlassTint.mode`, con un secondo campo interno, `modeOverlay`;
`drawBackdrop(onDrawLight, underlay)`; `FluidBarAction(tint)`; `FluidTabItem.filledIcon`) e tipi e letture nuovi
(`FluidGlassModel`, `LocalFluidGlassModel`, `GlassTintMode`, `FluidGlassDiagnostics`, `FluidIcons`, `FluidIcon`,
`LocalFluidIconSize`, `FluidMetrics`). La compatibilità binaria non è provata: l'engine si integra come sorgente.
`GlassTint` è una `data class`: `copy` e le sue prime tre componenti restano come prima, ma `equals` e `hashCode` vedono
ora anche il modo, quindi due tinte uguali nella 3.x con modi diversi non lo sono più. Cambia, da Android 13, quello che
si vede.

### Chi sceglie, e come

`LocalFluidGlassModel` dice quale materiale chiede un sottoalbero; di serie `Liquid`. È una **richiesta**: `Classic`
si ottiene sempre, `Liquid` solo dove può esistere. Per ogni superficie decide una funzione pura,
`resolveFluidGlassModel`, con sei domande in quest'ordine; la prima che risponde «no» lascia il vetro di prima.

1. **Il sottoalbero chiede `Liquid`.**
2. **Il dispositivo ha AGSL** (Android 13, livello 33; il livello simulato di una build di debug conta, vedi «La
   diagnostica»).
3. **La superficie non è la fascia in alto** (`GlassFalloff.FadeDown`). La fascia delle schermate dell'engine non è più
   un vetro sotto il materiale nuovo: lì la sostituisce il bordo dello scorrimento (`FluidScrollEdge`, [schermata.md](schermata.md)),
   che non è una lastra. Resta la fascia, e quindi `Classic`, dove il bordo non c'è (sotto Android 13, con `Classic`, con un
   compilatore che rifiuta la striscia) e per un `glassSurface(falloff = FadeDown)` scritto a mano da un'app.
4. **La forma è una che il campo sa rendere**: `RectangleShape` o una forma ad angoli tondi (`RoundedCornerShape`,
   `AbsoluteRoundedCornerShape`, `CircleShape`, le `ContinuousCornerShape` dell'engine, capsula compresa). Il
   materiale nuovo ritaglia la sagoma con lo shader, e una sagoma che lo shader non conosce uscirebbe col contorno
   sbagliato: una `CutCornerShape`, che pure è una `CornerBasedShape`, usciva con gli angoli tondi, e resta `Classic`
   come ogni sottoclasse di `CornerBasedShape` scritta da un'app. Su un contorno generico il vetro di prima spegne la
   lente e tiene il resto, che è il ripiego onesto. **La taglia non conta più** (dalla 4.0, F1B.1): Compose riduce due
   raggi sullo stesso fianco solo quando superano insieme il lato corto, quindi un angolo può scendere oltre mezzo
   lato (`RoundedCornerShape(topStart = 80.dp, …)` su un lato corto di 100, il fumetto di chat `22/22/22/6` alto 40
   dp), e il campo lo rende col suo ramo largo, col contorno di Compose al pixel. Quelle forme sono `Liquid` a ogni
   taglia e non cambiano vetro quando crescono: in una chat c'è un vetro solo. (Fino alla prima stesura della 4.0
   restavano `Classic` a quella taglia, e si misuravano al layout con `GlassFieldFit`, che oggi per ogni forma che il
   campo sa rendere non misura niente.)
5. **L'ottica ha uno spessore**: lente, bordo oppure ombra. Un'ottica senza nessuno dei tre è un velo che sfoca appena
   e scurisce, cioè lo **scrim di un modale**, e il materiale nuovo gli darebbe la smerigliatura e l'ombreggiatura di
   una lastra grande quanto lo schermo.
6. **Il compilatore del dispositivo accetta il corpo e la luce della lastra** (`GlassBody1ShaderString` e
   `GlassLight1ShaderString`). Si chiede per ultima perché è l'unica domanda che compila: una superficie che resta
   `Classic` per un'altra ragione non deve far compilare i programmi nuovi. Se ne cade uno solo, la superficie resta
   `Classic` per intero: mai un corpo nuovo con il bordo di prima.

La risposta si ricorda insieme alla forma, alla caduta, all'ottica e al modello chiesto: una superficie sceglie quando
entra in composizione, e di nuovo solo se uno di questi cambia, mai a ogni fotogramma. `glassSurface`,
`glassControlSurface` e l'ombra staccata della barra flottante chiedono la stessa cosa nello stesso modo: è così che
un tasto sa se il bagliore del dito passa dalla luce del materiale nuovo o resta quello di prima. Perché una superficie
ha preso un materiale si legge nella diagnostica (sotto).

### Cosa passa oggi dal materiale nuovo, e cosa resta il vetro di prima

| superfici | materiale | perché |
|---|---|---|
| tasti di vetro e chip (`glassControlSurface`), capsula flottante, campo di ricerca, pannello della tab bar, rail, `FluidSidebar`, foglio, `FluidDrawer`, notifica, suggerimenti, fast scroller, le superfici di `engine-ai-ui` | `Liquid` | passano da `glassSurface` o `glassControlSurface`, con forme dell'engine |
| card e gruppi lista con `glass = true` (ruolo `Content`) | `Liquid`, senza ombra | lo stesso: il vetro sul contenuto resta disponibile, e dove un'app lo chiede è il materiale nuovo |
| la barra in alto di `FluidScreen`, della barra che si comprime, della barra ancorata e di `FluidPageChrome` | nessuno: il bordo dello scorrimento | sotto `Liquid` il contenuto si scioglie nel colore del fondo, senza lastra (`FluidScrollEdge`, [schermata.md](schermata.md)) |
| la fascia in alto, dove il bordo non c'è (sotto Android 13, con `Classic`, striscia rifiutata) e un `glassSurface(falloff = FadeDown)` scritto da un'app | `Classic` | `FadeDown`, domanda 3 |
| lo scrim dei modali | `Classic` | un velo senza spessore, domanda 5 |
| popover, menu che nascono da un tasto, menu contestuale, finestra che si espande, barra fra le pagine **in viaggio**, ogni `fluidPhysicsSurface` | `Liquid` al livello `Full`, fermi e in viaggio | Fluid-physics sceglie con `resolvePhysicsGlass` (le stesse domande, più il livello: a `Balanced` e `Lite` resta il vetro di prima). Corpo, luce e ombra sono quelli di `glassSurface`, con la luce e l'ombra nel corpo |
| la pillola della tab bar e la riga nascosta che campiona da premuta | `Liquid` | il vetro della barra un po' più scuro, `GlassPuckProfile`, derivato dal profilo di serie |
| la goccia: pomelli di cursore e interruttore, pillola del segmentato, indicatore del rail, lente dell'indice, goccia delle voci dei menu | `Liquid` | `glassDrop` con `GlassDropProfile`, derivato dal profilo di serie: faccia piena a riposo con l'ombra dei tasti, senza filo né riflesso (arrivano con la pressione), lente limpida sotto il dito |
| il vetro dipinto: `fluidStaticGlassSurface`, il foglio e l'avviso di piattaforma (fuori da un `FluidGlassModalHost`) | `Liquid`: dipinto dalla ricetta | sceglie con `paintedGlassTakesLiquid`, le stesse domande di `resolveFluidGlassModel` senza la forma; con `Classic`, sotto Android 13 o con le sorgenti rifiutate è il dipinto della 3.x per intero (sotto, «Il vetro dipinto») |
| l'orologio (`engine-wear`) | `Classic` | `FluidWearTheme` lo chiede: il materiale nuovo sull'orologio è un incarico a parte |
| tutto, sotto Android 13 o dove il compilatore rifiuta | `Classic` | domande 2 e 6 |

**Un materiale, fermo e in viaggio.** Fino alla 4.0 la barra ancorata delle pagine e i menu che nascono da un tasto
cambiavano materiale a vista fra riposo e viaggio, e la pillola, la goccia e i pomelli erano un altro vetro sopra barre
nuove. Oggi tutte le strade passano dalla stessa ricetta (`glassRecipe`), e un test le confronta sugli ingressi:
`GlassRecipeParityTest` (una superficie fisica ferma riceve gli stessi numeri della `glassSurface` della stessa
forma; i profili della pillola, della goccia e del dipinto seguono quello di serie; nessuna strada prende il materiale
nuovo sotto Android 13 o con `Classic`). La pagina «Parità» della galleria le mette una accanto all'altra sullo stesso
fondo, per guardarle sul dispositivo.

**Che cosa non è ancora del materiale nuovo.** La sfocatura «gommosa» che la tab bar fa mentre si ripiega
(`Modifier.gooey`, `FluidGooey.kt`) resta quella di prima: è un effetto sul pannello della barra, non una strada del
materiale, e passa al materiale nuovo con la tab bar che si riduce a un tasto, in una fase successiva del progetto.

### Un'app che aggiorna senza toccare il codice

**L'aspetto delle lastre di serie.** Da Android 13 ogni lastra della prima riga della tabella cambia aspetto, verso il
Liquid Glass di Apple, con le scelte fatte da Alessio sulle tavole di confronto (11 novembre 2026: bordo e velo di
iOS 27, ombra e smerigliatura di iOS 26, sul tema scuro riflesso e filo chiaro). Sul lato chiaro: un **filo scuro**
sottile e di spessore assoluto su tutto il perimetro (più leggero in alto e in basso); subito dentro, in alto e in
basso, una **riga chiara** con un alone che rientra nel corpo; un **velo** quasi bianco che si ispessisce dove lo
sfondo gli somiglia (su una pagina chiara il corpo sta appena sopra la pagina, su bianco puro appena sotto, su un
colore saturo lascia passare circa metà); un'**ombreggiatura** che scurisce il corpo verso i bordi; un'**ombra larga e
morbida**, una sola componente scostata in basso (raggio 34 dp, come sotto una capsula di iOS 26); e una
**smerigliatura** di 4,5 dp, quella di iOS 26, per cui dietro una barra le parole restano macchie che si riconoscono
come forme. Su una pagina chiara un tasto non stacca per il colore del corpo (nemmeno quello di Apple lo fa): stacca
per filo, riflesso e ombra. Sul lato scuro: un **filo chiaro** su tutto il perimetro, pieno in alto e in basso e un
poco più tenue sui fianchi, con un riflesso più forte che sul chiaro; un **velo** grigio che sui tasti e sulle capsule
è chiaro (il corpo sta a 67 su una pagina `#0D0D0F`) e sulle superfici grandi, che portano testo, è più profondo
(fogli, card, pannelli e menu a 34: il testo secondario dell'engine vi si legge a 6,9:1, e a 6,3:1 sopra il fondo un poco più chiaro che un foglio ha sul tablet); e un'ombra più forte, che si
vede solo sopra qualcosa di chiaro. La lente c'è ancora, nella fascia del bordo: con la smerigliatura di iOS 26 si
vede di più che sotto quella di iOS 27. I numeri di serie, con la fonte di ciascuno (una misura di iOS 27 o 26 dove
c'è, «a occhio» con la data dove non c'è), stanno nel profilo, `GlassLiquidProfile.kt` (`GlassLiquidProfile.Shipped`),
e solo lì; le alternative delle tavole restano nel profilo come varianti, raggiungibili dalle manopole di una build di
debug (sotto, «La diagnostica»).

**Le ottiche scritte a mano.** In `Liquid` smerigliatura, lente, bordo e ombra li decide il profilo e non il ruolo: è
ciò che fa sembrare tasto, capsula, menu e pannello lo stesso vetro in quattro spessori. Una `GlassOptics` passata a
`glassSurface` vale allora come **attenuatore**. Per ogni campo che si legge c'è una soglia, il valore più basso che
quel campo ha fra le ottiche di serie: `blurScale` 0,8, `refractionHeight` 7 dp, `refractionAmount` 9 dp,
`highlightAlpha` 0,45, `shadowAlpha` 0,36. Dalla soglia in su il materiale è quello del profilo, sotto ne resta la
stessa frazione, a zero niente. Quindi:

- **chiedere meno della soglia dà meno**: un'ottica con `shadowAlpha = 0` non ha ombra, una con la lente a zero non
  piega, una con `blurScale` a metà soglia ha metà smerigliatura. La regola esiste per questo: chi aveva spento
  qualcosa deve continuare a non averlo. Vale la soglia, non il ruolo: un'ottica di barra con `blurScale` 2 invece di
  5 è ancora sopra 0,8, e ha la smerigliatura piena;
- **chiedere più sfocatura del ruolo dà di più**, ed è l'unica eccezione agli attenuatori: un `blurScale` oltre quello
  di serie del ruolo (il più alto fra le ottiche di serie che l'engine usa con quel ruolo: `Bar` 5, `Modal` 3,5,
  `Floating` e `Content` 1,8, `Interactive` 1,4) fa crescere la smerigliatura del profilo nella stessa proporzione,
  fino a tre volte. Un pannello `Modal` che chiede `blurScale = 7` per nascondere una copertina ha due volte la
  smerigliatura di un pannello di serie (9 dp prima della regola della taglia, 13,5 dp a pannello pieno), ma meno
  della 3.x: il pannello del lettore di Fluidify esce a 11,5 dp contro i suoi 17,5 (sotto, «I limiti dichiarati oggi»). Le altre
  voci dell'ottica non crescono: lente, bordo e ombra restano quelli del profilo;
- `vibrancy` non è un attenuatore: 1 resta «nessun filtro», 1,6 (quella di ogni ruolo) dà la saturazione del profilo,
  di più dà di più. `dispersion`, `pressedDepthBoost`, `edgeFloor`, `backdropResolution` (con un pavimento: la
  cattura non scende sotto il 40% della risoluzione) e `sizeResponse` valgono come prima;
- **non si leggono** `depthEffect` (la cupola dipende dalla taglia), `highlightWidth` e `highlightAngle` (il filo è del
  profilo), `shadowRadius` e l'ombra interna. E non si legge il raggio del fondale (`rememberGlassBackdrop(blurRadius)`,
  `DefaultGlassBlurRadius`): la smerigliatura è del profilo.

**Le tinte: tre modi.** `GlassTint` ha un campo in più, in coda, `mode` (`GlassTintMode`), che dice al materiale nuovo
cosa vuol dire `overlay`. Il vetro di prima non lo legge: lì `overlay` è sempre la pellicola dipinta sopra la lente.

- **`Fixed`**: la pellicola dipinta com'è, **al posto** del velo del materiale. È il comportamento di sempre ed è il
  default del costruttore, quindi ogni tinta scritta a mano a tre argomenti. Sono `Fixed` anche le pellicole che sono
  pellicole per mestiere (`darkBarTint`, `lightBarTint` e le loro sorelle sopra una foto, `selectionTint`) e la
  pellicola d'accento del tasto selezionato.
- **`Neutral`**: il velo lo decide il materiale, e `overlay` serve solo al vetro di prima. Sono così le tinte di
  famiglia di `GlassDefaults`: `barTint`, `floatingTint`, `controlTint`, `contentTint`, `stackedContentTint`,
  `modalTint`, e `panelTint` senza colore.
- **`Colored`**: una tinta alla Apple. `overlay` colora il vetro **sopra** il suo velo, e la sua luminosità segue ciò
  che il vetro ha dietro (su nero scende al 58% del suo valore, su bianco resta piena). È così `tintedModalTint`, e
  quindi `panelTint` con un colore.

**La regola che conta per un'app: una tinta a cui l'app ha dato un colore resta dipinta com'è.** `Neutral` vale solo
per la pellicola con cui la tinta è nata. Un'app che dà un tono a una lastra lo fa quasi sempre così:
`GlassDefaults.barTint().copy(overlay = unColore)`. Se `Neutral` sopravvivesse a quella `copy`, il materiale nuovo
quel colore non lo dipingerebbe, e l'app cambierebbe aspetto senza aver toccato una riga. Quindi la tinta ricorda la
pellicola per cui è stata detta neutra (un campo interno, che `copy` porta con sé), e se `overlay` non è più quella si
legge `Fixed`: il colore dell'app, dipinto com'è, come prima. Non conta l'ordine delle chiamate né chi le fa, conta
solo se il colore è ancora quello; cambiare `fallback` o `hairline` non tocca il modo. Chi vuole davvero un'altra
pellicola per il vetro di prima e il velo per il nuovo costruisce la tinta per intero dicendo il modo:
`GlassTint(overlay, fallback, hairline, GlassTintMode.Neutral)`. Il modo con cui il vetro si disegna davvero lo dà
`glassTintLiquidMode`, pura.

**Il contrasto di sistema.** Sopra lo standard (contrasto medio o alto, Android 14+) `forContrast` rende la pellicola
più densa come ha sempre fatto **e** la mette in `Fixed`, qualunque fosse il suo modo: il materiale nuovo dipinge allora
la pellicola densa di prima al posto del velo. Il velo `Neutral` è tarato per lasciar passare lo sfondo, e una tinta
`Colored` scurisce col fondo: chi chiede più contrasto non vuole né l'una né l'altra cosa. Misurato sul banco: col
contrasto alzato il testo sul vetro nuovo si legge quanto su quello di prima al centro della lastra, fino al 5% in meno
sul bordo di un'icona, dove il corpo si ombreggia. **A contrasto standard no**: vedi «I limiti dichiarati».

**Tenere il vetro di prima.** Una riga, attorno a una schermata o alla radice dell'app, ovunque stia sopra il vetro:

```kotlin
CompositionLocalProvider(LocalFluidGlassModel provides FluidGlassModel.Classic) {
  // la schermata, o l'app: il vetro qui dentro è quello della 3.x
}
```

Ha senso per una schermata che conta su ottiche che chiedono più del ruolo, o che sta quasi sempre sopra fondi del tono
opposto al tema (copertine scure in un'app chiara) e ha pannelli grandi con testo, che dall'adattività non guadagnano (la sezione «Il vetro che si adatta»
dice chi si adatta); per una pagina che mette i due vetri a
confronto; per un modulo che non è ancora passato al materiale nuovo, come l'orologio. Non serve «per sicurezza»: dove
il materiale nuovo non può esistere l'engine torna al vetro di prima da solo. Vale per le superfici sotto quel punto,
che rifanno la scelta se il valore cambia.

### Com'è fatto

Quanto basta a usarlo e a non romperlo; ogni passo, con i suoi numeri e le sue fonti, è nel KDoc dei file di
`ui/glass/field/`.

**Il corpo** è ciò che il vetro fa allo sfondo, ed è **una catena sola** sulla cattura, in un layer solo: saturazione →
sfocatura → mescola fra nitido e sfocato → lo shader del corpo. Lo shader piega lo sfondo nella fascia del bordo (la
lente, con la cupola sui piccoli e una frangia cromatica a tre campioni dove l'ottica la chiede), ci posa il **velo**,
la tinta e l'ombreggiatura, e ritaglia la sagoma con la propria copertura. Il velo si ispessisce per pixel dove lo
sfondo gli somiglia, misurando la somiglianza sul canale in cui distano di più: è ciò che dà insieme un corpo appena
grigio su bianco e un velo che lascia passare metà di un colore saturo, cosa che un velo costante non sa fare.
`intensity` qui è la comparsa del materiale: scala lente, velo, tinta, ombreggiatura, luce e ombra, e porta la mescola
dal nitido allo smerigliato **senza toccare il raggio di sfocatura**. È il punto: il raggio decide il margine della
cattura, e un margine che cambia rifà la cattura a ogni fotogramma. Il margine si calcola una volta, a qualità piena,
come sfocatura meno fascia della lente, e resta fermo mentre intensità, qualità e pressione si muovono. La qualità
adattiva fa ciò che faceva: scala lente, luce e ombra, porta la sfocatura fra il 45% e il pieno, spegne la frangia
sotto 0,75 e cattura a metà risoluzione sotto 0,7; il velo mai.

**La luce** è il filo, la riga chiara con il suo alone, e il bagliore del dito. Non legge lo sfondo, e si disegna sopra
il corpo e sotto il contenuto in **due disegni dello stesso programma**: prima la luce che **somma** (`Plus`: riga,
alone, bagliore), poi il filo che **copre** (`SrcOver`). Due modi perché le misure di Apple sono quelle di una luce che
si somma (un riflesso composto in `SrcOver` chiedeva una forza diversa per ogni fondo) e un filo scuro deve coprire; e
quell'ordine perché il bagliore schiarisca il corpo e il filo resti pulito sopra, invece di assottigliarsi sotto il
dito. Sul lato scuro di serie il filo non c'è, e il disegno è uno. La luce passa da un parametro di `drawBackdrop` nato
per lei, `onDrawLight`, che sta **fuori** dal materiale esportato: il vetro annidato campiona il corpo del pannello, mai
il suo bordo né il suo bagliore, e un bagliore che cambia a ogni fotogramma non fa ricatturare chi sta dentro. Non è un
layer in cache: si ridisegna a ogni fotogramma in cui la superficie si ridisegna, ed è un `drawRect` con uno shader
che non legge texture (sul prototipo di S1, 0,1 ms per tre elementi). Lo shader è un'istanza sola per processo, quindi
i suoi uniform si impostano a ogni disegno, subito prima di disegnare.

**L'ombra** sono due maschere sfumate dal contorno della forma ferma, contatto (stretta) e ambiente (larga, scostata in
basso), registrate **in sola alfa** in un layer solo; colore e forza li applica il layer quando si posa. Così cambiarli
(un'intensità che sale, il lato del tema che gira, e più avanti l'adattività) non rifà le maschere, che sono sfocate
sulla CPU e ricaricate sulla GPU a ogni nuova registrazione. Il nodo dell'ombra sta **sotto il layer del tasto**:
`drawBackdrop` ha un secondo parametro nuovo, `underlay`, che posa dei modificatori dentro la trasformazione di
`layerBlock` e fuori dal ritaglio, dove stavano già l'ombra e l'anello di sempre. Senza, un tasto che cresce sotto il
dito lascerebbe la propria ombra dov'era. Un'ottica senza ombra (`Bar`, `Content`) non monta il nodo.

**Il campo e l'angolo.** Maschera, lente, filo, riflessi e contorno dell'ombra escono da una funzione sola, il campo di
distanza della sagoma: corpo e luce sono due programmi che devono vedere la stessa sagoma, e la vedono perché
includono lo stesso testo. Il campo conosce l'**angolo continuo** dell'engine: sui pannelli grandi una superellisse
adattata alla costruzione di `ContinuousCornerShape` si scosta dalla sua curva al più dello 0,44% del raggio, dove un
arco di cerchio arrivava all'1,25%. Le forme piccole (lato lungo sotto i 256 pixel dello schermo) l'engine le disegna
già come rettangoli arrotondati al raggio equivalente, e il campo fa lo stesso; una capsula è un semicerchio esatto. Il
ritaglio hardware resta un rettangolo arrotondato, perché un ritaglio su un path generico farebbe mascherare il nodo
fuori schermo a ogni fotogramma, e per le lastre raccordate sta al **98%** del raggio nominale: il raccordo esce di
poco dal cerchio attorno alla diagonale, e un ritaglio al raggio pieno taglierebbe lì il filo, che è largo due o tre
pixel. `RoundedCornerShape` e `AbsoluteRoundedCornerShape` prendono i loro quattro raggi come angoli circolari, **con
la riduzione di Compose** (le coppie sullo stesso fianco, contro il lato corto) e quella di Skia (tutti insieme, se due
raggi accanto superano un lato); le `ContinuousCornerShape` grandi la riduzione di Compose e poi il limite a mezzo lato
dell'emettitore. Il campo rende esattamente quel contorno quando ogni angolo sta entro mezzo lato corto
(`GlassSlabGeometry.fits`, provato contro `Shape.createOutline` in `GlassFieldOutlineTest`), e dalla 4.0 (F1B.1) anche
quando un angolo supera mezzo lato corto perché quello accanto gli lascia il posto: lì il nucleo prende un ramo largo,
sempre col contorno di Compose. Una `CutCornerShape` e ogni forma che non è ad angoli tondi restano `Classic` (fino al
ritocco del profilo una `CutCornerShape` usciva con gli angoli tondi).

**La ricetta e il profilo.** Tutti i numeri che una lastra riceve li calcola una funzione pura, `glassRecipe`, da ruolo,
ottica, taglia, lato del tema, intensità, profondità, qualità e pressione. I numeri **di gusto** (spessore e colore del
filo, forza del riflesso, velo, ombra, smerigliatura, lente, quanto crescono i grandi) non stanno né negli shader né
nella ricetta: stanno nel profilo, `GlassLiquidProfile`, ognuno con la sua fonte. Il profilo tiene anche il catalogo
delle varianti che vanno in tavola (iOS 27, iOS 26, «oggi»): sono numeri, non altri shader. Le scene del banco, le
misure contro Apple, le tavole di gusto e le immagini di riferimento escono dalla ricetta, non da numeri ricopiati, e un
test confronta uniform per uniform ciò che l'innesto passa agli shader con ciò che le scene scrivono: ciò che si sceglie
su una tavola è ciò che l'engine disegnerà.

**La taglia.** Una lastra grande smerigliata come una piccola sembra una piccola ingrandita. La regola della taglia
dell'engine (lo stesso guadagno di prima: zero fino a 64 dp di lato corto, pieno da 320 dp, per `sizeResponse`
dell'ottica) fa crescere **smerigliatura e ombra**: di serie, a guadagno pieno, la sfocatura di una volta e mezza (da
4,5 a 6,75 dp), i raggi dell'ombra per 1,3 e le sue alfa per 1,2: l'ombra di serie è già larga, e un pannello deve
sembrare più in alto di una barra, non portare un alone enorme. **Il filo no**: lo spessore del filo e il rientro del
riflesso sono assoluti, gli stessi su un tasto, una capsula, un menu e un pannello, perché è così in iOS e perché un
filo che cresce diventa una fascia. La lente è una frazione del lato corto con un tetto (su un pannello è un bordo, non
mezzo pannello), e la cupola sparisce sopra i 120 dp: un pannello non è una goccia. Due regole in più, ai due estremi:

- **sul tema scuro il velo si fa più profondo con la taglia**: da 48 a 96 dp di lato corto passa, senza scatti, da
  quello dei tasti a quello dei grandi, alla stessa trasparenza. Una capsula (56 dp) ne prende il 7%, una tab bar
  (62 dp) il 20%, una notifica a una riga circa metà, una card da 96 dp in su tutto. Il motivo è il testo: col velo
  chiaro dei tasti un foglio scuro diventava un grigio medio, e il testo secondario dell'engine scendeva a 3,8:1 (sul
  tablet); col velo dei grandi torna sopra 6:1, come sul vetro di prima. Sul tema chiaro il velo non cambia;
- **i piccolissimi** (lato corto sotto 44 dp: un indicatore da 26 dp, un pallino) hanno l'ombra in scala col lato e
  un riflesso più tenue e meno profondo (a 26 dp il 51% della forza, rientro 4,2 dp invece di 7): senza, il riflesso
  pensato per un tasto faceva dentro il filo un anello chiaro vistoso e l'ombra era più grande dell'oggetto. Il filo
  resta quello di tutti.

**Il vetro annidato** (un tasto dentro una notifica, un'azione su un foglio, un `glassSurface` che un'app mette dentro
un altro) campiona il materiale del pannello che lo contiene, che ha già il suo velo: posarne un secondo pieno
copriva metà di quel materiale, e sul tema scuro il tasto usciva una toppa grigia sopra una notifica tinta, o una
toppa più chiara su un foglio scuro (sessione sul tablet S1-ter). Quindi una lastra annidata ha **il velo a un terzo
sul tema scuro** (×0,35: sul banco il tasto nella notifica verde passa da luma 85 a 64 sopra un pannello a 54, e
prende il verde; nel foglio scuro da 76 a 48 sopra un foglio a 34) e **l'ombra a metà** sui due temi (un tasto sta un
gradino sopra il pannello, non sopra la pagina: piena, sopra un foglio chiaro, era una nuvola grigia). Sul tema chiaro
il velo resta: lì il vetro su vetro era già pulito. Filo, riflesso, lente, smerigliatura, una pellicola dipinta com'è e
la tinta di Apple non cambiano. **Annidato lo dice il nodo**, non un parametro: è la stessa domanda con cui il vetro su
vetro (2.13.0) trova il pannello fra gli antenati, quindi vale da sola anche per i pannelli di un'app. Lo dice anche
il **fondo** (4.0.0, F1A.13): una lastra che campiona il materiale pubblicato con `exports` da un pannello `Liquid` sta
su quel pannello anche se non ne è figlia, cioè lo schema di `exports` con i controlli in un nodo fratello del
pannello e `rememberCombinedGlassBackdrop(pagina, materiale)`. Un vetro **accanto** a un altro (due tasti di una
barra, la capsula sopra la pagina) resta una lastra sulla pagina, come una superficie con `nestInContainer = false`;
le azioni della barra in alto campionano ciò che la barra pubblica (il contenuto già sciolto del bordo dello scorrimento,
o il materiale della fascia di prima, che non pubblica) e restano lastre sulla pagina anche loro. Un pannello **pieno** dell'engine con dentro del vetro (la notifica quando l'host non
ha un fondo, il foglio del modale senza fondo) pubblica il proprio colore come materiale e fa da contenitore: i suoi
tasti sono vetro annidato e ne prendono il colore. Prima campionavano il fondo vuoto, e sul Tab S9 erano un anello
vuoto con la smerigliatura di serie e una toppa grigia con una più larga (sotto, «Da sapere»). Col vetro di prima il
pannello pieno non pubblica niente. I numeri stanno nel profilo (`GlassLiquidNestings`), e la manopola `nested=off`
rimette il vetro annidato di prima per confrontarlo sul tablet.

**Il costo.** Una lastra `Liquid` ferma tiene 4 nodi e 3 layer (cattura, posa, ombra), e 3 e 2 senza ombra; una di
prima ha 5 nodi e, dei layer, i 2 che la diagnostica legge (cattura e posa). I tre nodi del vetro di prima che stanno
nei file di Kyant (ombra interna, ombra, anello) non si contano ancora, e i loro tre layer si deducono dal codice: il
«5 e 5» del test in Robolectric è quella somma, non una lettura del dispositivo (sul tablet: 4 nodi e 3 layer, 5 e 2,
3 e 2). La risoluzione della cattura si decide sulla smerigliatura del ruolo, prima della regola della
taglia (in composizione, dove la taglia non c'è ancora): con i 4,5 dp di serie ogni ruolo cattura al 73% della
risoluzione (le card, che chiedono metà risoluzione, al 40%), e una lastra grande, che sfoca un poco di più, resta
lì (dalla parte sicura). Una smerigliatura oltre i
10 dp (una variante delle manopole, o un'ottica che chiede più del ruolo) scende fino al 40% e non oltre, perché la
sagoma qui la disegna anche il corpo, dentro la cattura, e al 40% il suo bordo sfumato è già largo tre pixel dello
schermo. Con un `layerBlock` (un tasto che cresce sotto il dito) la cattura resta piena, come prima. Una
pressione cambia un uniform del corpo a ogni fotogramma, e un `RenderEffect` è immutabile: si ricostruisce l'ultimo
anello della catena, mentre il pezzo a monte (saturazione, sfocatura, mescola) resta finché non cambia. Sul tablet
(un Tab S9, sessione S2-pre, profilo di serie) premere un tasto costa **0,7 ms** di GPU in più del vetro di prima
(mediana 3,15 contro 2,45 ms, a pari fotogrammi persi; con la combinazione di prima delle scelte, quella di S1-ter, era
2,92), perché premere rifà la catena del corpo e disegna la luce in due passate: la cattura al 73% spiega solo circa
0,1 ms (3,15 contro 3,03 con la cattura al 40%). Una lista che scorre sotto una barra non peggiora le scadenze (GPU
mediana 2,37–2,47 contro 2,2 ms, fotogrammi oltre la scadenza 17,5–20,0% contro 20,8–29,2% del vetro di prima), e la
cattura al 73% vi pesa circa 0,1 ms (2,43 contro 2,33 con la cattura al 40%). Il costo di una pagina piena di vetro,
e su una GPU più debole o sull'S25, non è misurato.

### Il vetro che si adatta (4.0)

Da Android 13, col materiale `Liquid`, un vetro sa che cosa ha dietro. Non è un'opzione che si accende: è ciò che il vetro
fa, e le app non chiamano niente. Il lavoro si divide in tre: una **sonda** misura lo sfondo, un **filtro** decide da che
parte sta il vetro, e il **materiale** (la ricetta, `glassRecipe`) trasforma la misura in velo, ombra e tinta. Ogni passo
ha i suoi numeri e le sue fonti nel KDoc di `ui/glass/field/` (`GlassProbe.kt`, `GlassProbeScheduler.kt`,
`GlassAdaptiveFilter.kt`) e di `ui/fluid/FluidGlassAdaptive.kt`; qui c'è quanto basta a usarlo e a non romperlo.

**Cosa misura la sonda.** Su una miniatura dello sfondo sotto il vetro (registrata a 64 px e ridotta a blocchi a 16×16, così
il testo che scorre di meno di un pixel non fa tremare la media) calcola la luminanza media (0,2126 R + 0,7152 G + 0,0722 B sui
valori sRGB a 8 bit), la varianza, il colore medio, la copertura (una lettura che copre meno di metà dei pixel non si
consegna) e la **trama**: l'energia dei gradienti fra pixel vicini, che separa il testo da un fondo piatto meglio della
varianza (0 sotto 0,01, 1 sopra 0,06 della sua radice). Legge con un **lettore per finestra** (un `ImageReader`, un
`HardwareRenderer` suo e un filo di lavoro), partendo dopo il disegno della finestra: il filo principale non si ferma, e
una richiesta che serve più vetri costa come una. La sonda sta solo sulle **lastre radice**; un vetro dentro un altro
vetro non misura, e prende il lato da chi lo contiene.

**Quando legge.** Niente eventi, niente letture. Gli eventi sono tre: una **revisione** della sorgente (il layer che il vetro
campiona si è registrato di nuovo, ed è l'unico modo di sapere che sotto un vetro fermo lo sfondo è cambiato), un cambio di
**geometria** (il vetro si è spostato rispetto allo sfondo) e, per le sorgenti che non avvisano (un `CanvasBackdrop`), una
cadenza fissa di 500 ms. Al più una lettura ogni 120 ms; la sonda tace durante un movimento veloce (sopra 500 dp/s di
spostamento relativo, e per 100 ms dopo) e sotto il livello 0,9 di `FluidGlassQuality`, cioè mentre la pagina corre. Una
lettura fallita raddoppia l'attesa fino a 2 s e si ritenta al più tre volte, poi si aspetta un evento. Una pagina ferma non
legge e non cattura: lo prova `GlassProbeQuietTest` sul computer (nessuna richiesta, nessuna cattura, nessuno stato scritto),
e sul tablet, a pagina ferma per otto secondi, la scheda «Materiale» e «Icone» non hanno disegnato un fotogramma.

**Come decide il lato.** Il filtro fa una media mobile della luminanza (costante di 200 ms), con **due soglie distanti**: sotto
0,45 il vetro va sul lato scuro, sopra 0,62 torna sul chiaro, in mezzo resta dove sta; dopo ogni giro non ne fa un altro
per 400 ms. Così un fondo che sta a metà, una scacchiera che passa sotto, una lettura che trema di un centesimo non lo
fanno saltare avanti e indietro. La **prima lettura decide subito** (un vetro che nasce sopra una foto scura non si mostra
prima chiaro), con le stesse soglie partendo dal lato del tema. Il filtro ha una scadenza: dopo l'ultima lettura la media
continua a muoversi, e se attraverserà una soglia lo farà a un istante calcolabile, quando nessuna lettura nuova arriverà
a ricordarlo. Tutti questi numeri sono ipotesi da tarare sul tablet (sessione S3), non misure.

**Cosa cambia nel vetro.**

- **Il lato e l'inchiostro** (il giro, sotto).
- **Il velo.** Sul lato chiaro ha un pavimento (il vetro chiaro sopra il nero passa da 112 a 140 su 255, dove Apple sta fra
  136 e 143); sul lato scuro i **grandi** hanno un velo più profondo di quello dei piccoli, con un soffitto che gli impedisce
  di salire sopra la parte chiara dello sfondo.
- **L'ombra**, che si rinforza sopra il testo o una foto, come in iOS 26: per la trama che la sonda misura sale di 4,9 volte
  (18% di scurimento sotto la capsula sopra il testo, contro 3,7% sul fondo piatto), con un tetto di 0,45 sulla somma delle
  alfa. È la scelta A di Alessio; il rinforzo a metà o nessuno sono nei profili (`GlassLiquidAdaptives`).
- **La tinta** `GlassTintMode.Colored`: la tonalità resta, la luminosità segue lo sfondo (piena su bianco, al 58% su nero).
  Per un tasto tinto il corpo prende **l'alone del colore del tasto e un filo chiaro**, come in iOS 26 (scelta C di Alessio,
  un'eccezione al filo scuro scelto per gli altri tasti). Si ottiene con una `GlassTint` a tre argomenti più `mode =
  GlassTintMode.Colored`, su un `glassSurface`; oggi non c'è un componente «tasto tinto» pronto.
- **Il cursore dell'utente** (sotto).

**Il giro dei piccoli.** Quando lo sfondo sotto un vetro piccolo passa da chiaro a scuro (o il contrario) il vetro **gira**: il
corpo scende in 300 ms (una curva che segue il corpo del tasto di Apple, quasi tutto nei primi 200 ms), l'inchiostro del
contenuto svanisce nel corpo in 110 ms, passando per un fotogramma a contrasto quasi zero, e risale dall'altra parte, pieno
a 700 ms. Non c'è un ritardo in più: i 130 ms di Apple fra lo scuro che copre tutto il tasto e il corpo che si muove li dà già
il filtro (il lato è deciso 128–158 ms dopo la copertura piena). Un giro che cambia idea a metà riparte dai valori di quell'istante
e si disfa in un tempo proporzionale alla strada che resta: mai uno scatto. La composizione vede solo **da che parte** sta
l'inchiostro, che cambia una volta per giro, nel fotogramma in cui il contenuto è trasparente; il resto lo scrive il disegno,
senza ricomporre.

**Chi gira e chi no.** Gira solo un vetro il cui contenuto **segue l'inchiostro** (il patto interno `GlassInk`): un vetro che
girasse con sopra il testo scuro del tema diventerebbe illeggibile proprio dal lato in cui doveva aiutare. Lo danno i
componenti dell'engine che sanno cosa disegnano dentro: `FluidGlassIconButton`, `FluidGlassButton` (non quello `selected`,
che è una pellicola d'accento e non gira) e `FluidFloatingCapsule`. **Non girano**: un `glassSurface` qualunque, la tab bar
(`FluidFloatingTabBar`), i pannelli, i fogli e i menu: tengono il lato del tema, con il velo, l'ombra e la tinta che
seguono comunque lo sfondo. `GlassInk` è interno, quindi oggi un'app non può far girare un vetro suo. **I grandi** (ruoli
`Modal` e `Content`) non girano mai: la ricetta li fa scurire **in continuo** con la quota di nero che hanno sotto
(R4 A6: da 143 a 48 su nero pieno in circa 1,5 s), ma solo se il loro contenuto segue l'inchiostro, e **nessun pannello
grande dell'engine lo fa ancora**: un menu o un foglio di serie tiene il lato del tema (sul tema scuro, con il velo
profondo dei grandi). La pagina «Adattività» della galleria (scena `grandi`) lo mostra, ed è una voce aperta.

**L'inchiostro.** Il vetro fornisce al suo contenuto `LocalContentColor` del proprio lato: sul lato del tema è l'inchiostro
del tema, al pixel di oggi; dall'altro lato è `#121214` sul vetro chiaro girato da un tema scuro e `#F2F2F5` sul vetro scuro
girato da un tema chiaro (R4 A6). Fornisce anche `LocalFluidGlassAppearance`, un `FluidGlassAppearance` con `isDark`,
`darkness` (0..1), `backdropLuminance` (NaN finché non è misurata) e `isMeasured`: chi disegna sopra il vetro e vuole
sapere da che parte sta lo legge da lì. Cambia **una volta per giro**, non a ogni fotogramma. Un `tint` scritto dall'app
resta quello dell'app: segue l'inchiostro solo chi legge `LocalContentColor`. L'accento resta per l'azione principale.
Misurato sul banco, a contrasto di sistema standard, **ogni riga del campione** (capsula, tasto, tasto con etichetta, barra,
modale; i cinque fondi; i due temi) sta sopra la soglia (testo 4,5:1, icone 3:1), con il minimo a 5,57:1: i difetti di
leggibilità sopra fondi del tono opposto al tema, che la 4.0 aveva dichiarato, sono chiusi. Alcune righe sono più basse
di quelle di prima (nel tema scuro la capsula su nero da 14,81 a 10,18), restando sopra soglia.

**`FluidGlass`.** È la configurazione del vetro come in SwiftUI: `FluidGlass.Regular` (di serie, smerigliato e adattivo),
`FluidGlass.Clear` (più trasparente, **non si adatta**) e `FluidGlass.Identity` (nessun vetro, senza cambiare l'albero: serve
ad accendere e spegnere il vetro senza perdere stato né animazioni), con `.tint(colore)` e `.interactive()`. `Clear` non ha
smerigliatura (la mescola va a uno, il raggio resta) e uno scurimento fisso del 35% sotto il vetro, come dice Apple, che
tiene leggibile ciò che gli sta sopra; va usato solo su contenuto ricco che regge lo scurimento e con sopra un contenuto
chiaro e marcato. **Oggi è solo il tipo**: il modificatore che lo posa (`Modifier.fluidGlass(glass, shape, backdrop)`)
nasce con il contenitore in F3.2, e nessun componente lo consuma ancora, quindi `Clear` non si vede sul dispositivo. Con
`Classic` e sotto Android 13 `Clear` è `Regular`. Lo scurimento di `Clear` che segue lo sfondo, invece del 35% fisso, è una
manopola di debug (`cleardim=follow`), non un comportamento.

**Il cursore dell'utente.** `LocalFluidGlassPreference` va da `FluidGlassPreference.Transparent` (0) a `Colored` (1), di serie
`Default` (0,5), e muove insieme la diffusione e il velo di ogni vetro nuovo, soprattutto la diffusione: all'estremo
trasparente il vetro ha poca diffusione e il testo dietro si legge piegato dalla lente; all'estremo colorato è una lastra
quasi opaca del colore dello sfondo, senza forma dietro. A metà è il vetro di serie, **al bit**. Non tocca `Clear`, il vetro
di prima e il vetro sotto Android 13. **Il valore lo conserva l'app**: la riga facoltativa di `fluidAppearanceSection`
(`glassPreference` e `onGlassPreferenceChange`, insieme) disegna il cursore con l'etichetta «Trasparente / Di serie /
Colorato» e restituisce il valore; l'app lo fornisce poi attorno al suo contenuto,
`CompositionLocalProvider(LocalFluidGlassPreference provides valore) { … }`, come fa col resto dell'aspetto. L'engine non ha
un campo nuovo nelle impostazioni. Gli estremi sono provati sul banco, ma il giudizio di Apple sui suoi (ritoccati nella beta
5 di iOS 27) è su una misura sola, di fiducia bassa.

**Con `Classic` e sotto Android 13.** Non c'è niente di tutto questo: nessuna sonda, nessun giro, nessun velo o ombra che
segua lo sfondo, nessun inchiostro che cambi, il cursore non fa niente e `Clear` è `Regular`. Il vetro è quello di prima,
al pixel. `LocalFluidGlassAppearance` è `null` fuori da un vetro adattivo, e `FluidGlassProbeDiagnostics.isAvailable` dice
se la sonda può esistere.

**Cosa costa.**

- **Nodi:** una lastra radice porta **un nodo in più** (la sonda), accanto ai 4 di una lastra `Liquid` ferma; un vetro
  annidato non ne ha. Con il bordo dello scorrimento in alto, in tema chiaro, la striscia monta una sonda sua per decidere se
  scurire.
- **Letture:** nessuna a pagina ferma; durante un cambio, al più una ogni 120 ms per finestra, e una richiesta al lettore
  legge tutte le sonde che la chiedono nello stesso fotogramma. Sul Tab S9 una lettura costa 2–2,7 ms sul filo principale nel
  tempo morto dopo il disegno e 0,2–0,4 ms sul filo di lavoro; letta in modo sincrono ne costerebbe circa 5, e quattro sonde
  ogni 100 ms farebbero perdere il 10% dei fotogrammi.
- **Memoria del lettore:** un lettore per finestra con **64 caselle** in una griglia 8×8, un'immagine da 512×512 px (1 MiB
  RGBA) per immagine, al più tre: **3 MiB per finestra**. Oltre le 64 sonde entrano prima i vetri più grandi e più vicini al
  centro; chi non entra non misura e tiene il lato del tema (chi viene spinto fuori da uno più importante tiene l'ultimo lato
  misurato). La pagina più carica della galleria («Icone») ne usa 33. `FluidGlassProbeDiagnostics.peakSlots` e
  `totalSlotsRefused` lo dicono.
- **Il resto:** la prima misura non si anima; i valori animati (luminanza, trama, colore) arrivano in 300 ms con una
  catena di animazioni che riparte solo se la lettura cambia di più di un centesimo (di due per trama e colore).

La sonda si legge da fuori con `FluidGlassProbeDiagnostics` (richieste, letture, fallimenti, risvegli per motivo,
`probe(backdrop)` per le prove strumentate): non è un'API per le app. Per guardare tutto questo sul tablet c'è la pagina
**«Adattività»** della galleria (`--es gallery.page adaptive`, `sample/…/adaptive/LEGGIMI.md`), con sette scene e righe in
logcat (`FluidAdattivo`).

### Il riscaldamento

Un programma AGSL si compila la prima volta che la GPU lo esegue, sul RenderThread: senza preparazione il primo tasto,
la prima capsula, il primo pannello di un processo pagherebbero quello scatto nel fotogramma in cui compaiono (sul Tab
S9, coi prototipi del materiale, 20–40 ms a programma a freddo). L'engine li compila prima, **dopo il primo fotogramma
e mai nel percorso dell'avvio**, che è già il fotogramma più lungo che un'app abbia.

- **Quando parte.** Alla prima superficie di vetro che entra in composizione in un albero che chiede `Liquid`, su un
  dispositivo che lo può avere e senza un rifiuto del compilatore, anche se quella superficie resta `Classic` per la
  forma, la caduta o il velo: la barra in alto di ogni `FluidScreen` è la fascia (o, dove c'è, il bordo dello scorrimento,
  che chiede da sé il riscaldamento del suo programma), spesso l'unico vetro della prima schermata, e aspettare la prima
  lastra nuova vorrebbe dire scaldare troppo tardi. Le app non chiamano niente.
- **Come aspetta.** La finestra deve aver disegnato almeno una volta, non ridisegnare da 120 ms, essere visibile, e la
  coda del filo principale dev'essere vuota. Poi si disegna **un programma per volta**, fuori schermo (un
  `HardwareRenderer` suo su un `ImageReader` grande quanto un tasto, che non tocca niente di ciò che è a schermo), e si
  torna ad aspettare. Un'animazione, uno scorrimento, un tocco rimandano il passo; una pagina che non si ferma mai non
  lo fa partire, e allora il primo vetro paga la compilazione com'era.
- **Cosa scalda.** Il corpo **con la sua catena** (il primo corpo che entra paga anche saturazione, sfocatura e
  mescola) e la luce nei suoi due disegni; e il percorso del filo principale (ricetta, catena, pennello), perché il
  disegno fuori schermo passa dalla stessa classe che disegna ogni lastra. Non scalda l'ombra, che sono due maschere
  sfumate e non uno shader: se serve, lo dirà la misura sul dispositivo.
- **Per famiglie.** La lastra (`Slab`, due passi: corpo e luce a un pezzo; la chiede ogni albero `Liquid`) e i gruppi
  (`Group`, tre passi: corpo a otto pezzi, luce a otto pezzi, corpo ad anello; la chiede una superficie di
  Fluid-physics che si disegna col materiale nuovo, quando entra in composizione). La luce a otto pezzi oggi non la
  disegna nessuno (Fluid-physics compone la luce nel corpo), ma la famiglia la scalda lo stesso. Una famiglia con una sorgente rifiutata si salta
  per intero; un disegno fuori schermo che fallisce ferma la sua famiglia per tutto il processo e lo scrive in logcat a
  livello errore (tag `FluidGlass`); una famiglia finita lo scrive a livello informazione.
- **Cosa costa (misurato).** Sul Tab S9 i due programmi della famiglia `Slab` si scaldano in 7,7–8,1 ms di filo
  principale (4,6–6,2 ms con la combinazione di S1-ter), senza nessuno shader rifiutato; nella pagina «Materiale»
  8,5–9,5 ms.
- **Cosa non fa (misurato).** Sul Tab S9, avvii a freddo, tre per lato: **non protegge la prima schermata**. Parte dopo
  il suo primo fotogramma, quindi un vetro che c'è già lì compila i suoi programmi in quel fotogramma, acceso o spento
  (RenderThread del primo fotogramma 54–61 ms acceso, 54–55 spento). E nella galleria non accorcia neanche la prima
  pagina aperta tre secondi dopo: il primo fotogramma di «Controlli» costa 45–50 ms di RenderThread acceso e 49–50
  spento, quello di «Materiale» 13 e 12–13. La causa non è isolata (la prima schermata della galleria ha già del vetro,
  che può aver compilato gli stessi programmi). Il caso per cui esiste, una prima schermata senza lastre nuove e la
  prima che entra dopo, sul dispositivo non è ancora misurato.

**Da sapere: un'app chiusa entro pochi secondi dal primo vetro ricompila al lancio dopo.** HWUI scrive la cache dei
programmi su disco circa quattro secondi dopo una compilazione (misurato sul tablet): un processo che muore prima non
la lascia, e il lancio seguente ricompila. Dal lato dell'engine non c'è rimedio; va saputo quando si misura un primo
fotogramma «a freddo», e quando un utente dice che l'app «la prima volta scatta».

### La diagnostica

`FluidGlassDiagnostics` dice da fuori quale vetro c'è in scena e se il dispositivo lo sa disegnare. Le letture nuove
della 4.0:

- **gli shader**: `shaderSourceNames` elenca le quindici sorgenti AGSL dell'engine (nove del vetro di prima e di
  Fluid-physics, sei del materiale nuovo: corpo e luce a un pezzo e a otto, corpo ad anello, striscia del bordo dello scorrimento), `shaderVerdicts` i verdetti già chiesti in questo processo (`null` se
  la sorgente compila, altrimenti l'errore del compilatore), e `compileAllShaders()` le compila adesso tutte. Non
  dipendono da `enabled`. Un rifiuto scrive comunque una riga d'errore in logcat (tag `FluidGlass`);
- **quale vetro, e perché**: con `enabled` acceso, `liveSurfaces` conta le superfici vive per materiale (è `Liquid`
  una superficie che porta la luce del materiale nuovo, Fluid-physics, pillola e goccia comprese quando lo prendono;
  tutto il resto, chi chiama `drawBackdrop` a mano compreso, è il vetro di prima; il vetro dipinto non è una
  superficie del nodo e non si conta) e `liveModelChoices` le risposte vive della scelta per motivo
  (`FluidGlassModelReason`, nello stesso ordine delle sei domande: `Liquid`, `ClassicRequested`, `ApiLevel`,
  `FadeDown`, `GenericShape`, `FlatVeil`, `ShadersRefused`). Le risposte non sono superfici: un tasto che resta
  `Classic` chiede due volte, l'ombra staccata di una barra chiede senza essere una superficie;
- **quanto costa tenerlo**: `liveNodes` e `liveLayers`, i nodi e i layer del vetro vivi per tipo
  (`FluidGlassNodeKind`, `FluidGlassLayerKind`). Non contano ancora i tre nodi del vetro di prima che stanno nei file
  di Kyant fuori da `drawBackdrop` (anello, ombra, ombra interna): una lastra `Classic` ne ha tre in più di quanti se ne
  leggono qui;
- **il riscaldamento**: `warmUp` dice a che punto è ogni famiglia (`FluidGlassWarmUpStatus`: stato, programmi già
  scaldati, tempo sul filo principale), anche a diagnostica spenta; `warmUpEnabled` lo spegne (il primo vetro paga
  allora la compilazione, ed è così che si misura quanto risparmia); `warmUpNow()` scalda tutto subito bloccando il
  filo principale, ed è per il test strumentato della galleria, mai per il codice di un'app;
- **Android vecchio, simulato**: `simulateApiLevel(context, 32)` fa rispondere il vetro come Android 12 (`RenderEffect`
  sì, AGSL no), `30` come Android 11, `null` torna al vero; `simulatedApiLevel` dice quale livello è in vigore. Le due
  domande che decidono i ripieghi (`isRenderEffectSupported`, `isRuntimeShaderSupported`) e la scelta del modello
  rispondono con quel livello, e l'engine disegna i **suoi** rami di ripiego sullo Skia e sul driver del dispositivo
  vero: serve a vedere su un dispositivo nuovo se un innesto ha cambiato quei rami, non prova la piattaforma vecchia.
  **Solo in una build di debug**: in un'app che non è `debuggable` la chiamata non fa niente e restituisce `false`, e il
  livello non si può simulare per tutta la vita del processo. Solo verso il basso, mai sotto 26; vale per ciò che entra
  in composizione dopo (si ricrea l'attività);
- **le manopole del profilo**: `tuneLiquidProfile(context, "edge=ios26,blur=24")` sostituisce, per il processo, pezzi del
  profilo del materiale nuovo (quattordici manopole a varianti, con le lettere delle tavole di gusto: tutta la
  combinazione, `profile=shipped|before`; bordo, velo, ombra, il tondo, smerigliatura, lente, grandi, bordo scuro,
  velo scuro dei tasti e dei grandi, il vetro annidato `nested=on|off`, il materiale in viaggio
  `travel=off|soft|strong` e il ritaglio emulato delle superfici fisiche `travelclip=on|off`, che è una manopola di
  diagnosi; in più `knobrim=on|off` (di serie `off`), che non è un numero del profilo ma la scelta della goccia: `on`
  rimette filo e riflesso sul pomello a riposo; diciannove a numero, fra cui `nestedveil`, `nestedlightveil`, `nestedshadow` e, per il viaggio,
  `travelclear` (quanto nitido entra nella mescola a moto pieno, 0..1), `travelbend` (di quanto cresce lo spostamento
  della lente, 0..3) e `travelneck` (per quanta parte del viaggio il collo nasce e muore, 0,05..0,5)),
  `liquidProfileKnobs` le elenca e `liquidProfileTuning` dice quali sono in vigore. Le alternative alle scelte di serie restano lì: la smerigliatura `frost=wide` (la C, larga e
  mista) e `frost=mid` (la M, 8 dp) accanto alla D di serie (`frost=ios26`), il bordo scuro della tavola
  (`darkedge=faint`), l'ombra corta (`shadow=ios27`), il velo scuro dei grandi uguale a quello dei tasti
  (`darklarge=same`), il vetro annidato com'era prima della sua regola (`nested=off`), e tutta la combinazione di
  prima delle scelte (`profile=before`). Servono a **fotografare le varianti sul dispositivo** prima di scrivere un numero nel
  profilo, ed è per questo che la galleria le espone (`gallery.tune`, e la pagina «Materiale»): non sono un modo di
  dare a un'app un vetro suo. **Solo in una build di debug**, come il livello simulato; senza manopole il profilo è
  quello di serie, e il disegno è identico al pixel (`GlassLiquidTuningTest`, e le immagini di riferimento del banco).

I conteggi, come `totalCaptures`, contano solo ciò che nasce **dopo** l'accensione di `enabled`: si accende prima di
aprire la schermata da guardare.

### I limiti dichiarati oggi

- **A contrasto standard, sopra un fondo del tono opposto al tema, il testo si leggeva peggio di prima: è chiuso con
  l'adattività.** Il velo nuovo, tarato per lasciar passare lo sfondo, faceva scendere sul banco il rapporto fra inchiostro
  e corpo sotto 4,5:1 (nel tema chiaro su nero la capsula flottante 4,92 → 3,76, il modale 5,26 → 3,79, la card 5,57 →
  3,78; nel tema scuro il modale su un grigio medio 5,63 → 3,72). Con il lato e l'inchiostro che seguono lo sfondo e il pavimento
  del velo, la stessa tabella rifatta (`artifacts/liquid-glass/bench/f2-3/contrasto.md`) dà ogni riga sopra soglia, minimo
  5,57:1. **Resta**: un vetro che non gira (tab bar, pannelli, fogli, un `glassSurface` qualunque) tiene il lato del tema e
  si serve del velo e dell'inchiostro del tema; il margine lì è quello della tabella, non di più. A contrasto medio e alto
  il vetro torna alla pellicola densa, come prima.
- **Il vetro annidato sul tablet.** In un foglio di vetro scuro (pagina «Materiale», scena `nested`) il tasto scende
  da 84 a 61 sopra un foglio a 40; nella notifica di vetro della radice la chiusura sta a 48 sopra la card a 35 (75
  senza la regola). Nella notifica **piena** di «Avvisi con azione» (l'host senza fondo) i tasti campionavano il fondo
  vuoto: una cattura senza operazioni, che la piattaforma non disegna, quindi niente corpo con la smerigliatura di
  serie e con quella di mezzo (il margine della cattura, `max(0, sfocatura − banda della lente)`, era zero), il velo
  pieno sopra il niente con una più larga, e nessuna regola dell'annidato perché la card non era un vetro. Corretto in
  F1A.13: la cattura apre sempre con un'operazione, e la card piena pubblica il suo colore (in scuro tasto 67 e chiusura 65 sopra la card a 56, col suo verde, con D, M, r12 e C; 87 senza la
  regola; in chiaro 240 e 238 sopra 233; il banco, scene `gusto/annidato-opaco-*`, dà gli stessi numeri). Un tasto di vetro
  su un contenuto opaco **senza** fondo e fuori da ogni pannello (il foglio a schermo intero) ha ora il velo pieno con
  ogni smerigliatura, come una lastra sulla pagina: prima, con quella di serie, era il solo filo. Dopo la correzione il
  tasto nella notifica piena sta a 65–67 su 56 in scuro e a 240 su 233 in chiaro, nel foglio `Liquid` a 61 su 40, i
  controlli fratelli di un pannello che esporta a 62 su 48; il test strumentato `GlassNestingInstrumentedTest` compone
  i quattro alberi e passa 5 su 5. **Limiti noti**: nella notifica **di vetro** in chiaro il corpo del tasto è uguale al
  pannello (239,9 su 240,2) e lo staccano solo filo e ombra (assegnato all'adattività, F2); il composer di
  `engine-ai-ui` cabla i suoi controlli con lo stesso schema e **non è stato visto**, perché non è nella galleria.
- **Il profilo di serie è stato visto su un solo dispositivo, un Tab S9.** In due sessioni (S1-ter, con la combinazione
  di prima delle scelte; S2-pre, con quella di serie) e dopo il giro di correzione: nessuno shader rifiutato, il
  riscaldamento in 7,7–8,1 ms di filo principale dopo il primo fotogramma; il bordo del tasto tondo e della capsula
  combacia col banco entro pochi livelli nei due temi (sul tondo scuro il picco del filo è +95 in alto contro +93, +70
  sui fianchi contro +60, +102 in basso contro +102; in chiaro la riga scura −50 contro −42, un pixel più in fuori); i
  fogli, le card e i controlli scuri hanno corpo 42, 43 e 38 e testo secondario a 6,2, 6,1 e 6,6:1 (con le
  zone misurate dopo la correzione del vetro annidato, 5,9:1, un decimo sotto 6:1); l'ombra sotto i grandi è quella del
  banco (−16 a ridosso, −10 a 16 dp) senza rettangoli; l'indicatore da 26 dp non ha anello; l'anello di luce dopo il
  riciclo di una lista e il ritorno dallo sfondo coincide con gli angoli, 0 pixel cambiati nel foglio; a 32 e a 30
  simulati l'app non cade e non prova shader; sopra il contrasto di sistema medio e alto le lastre tornano la pellicola
  densa. Visti lì anche i difetti: la barra fra le pagine resta **un fotogramma senza vetro** quando arriva (6 volte su
  6, nei due temi di sistema) e il materiale **cambia a vista** fra riposo e viaggio, tutti e due chiusi nella 4.0 (un
  materiale solo fermo e in viaggio; una decisione sola per il passaggio di consegne: sotto); sul tema chiaro il bagliore
  del dito porta la faccia al bianco pieno, come quello di prima; la **tab bar scura è più chiara della 3.1.2** (corpo
  71 contro 47) e la smerigliatura di serie dietro una tab bar **lascia più contrasto di iOS 26** (deviazione standard
  del testo 19,0 contro 10,2: pareggiarla chiederebbe gli 8–9 dp della manopola `frost=mid`, ed è la conseguenza della
  scelta «4,5 dp dappertutto»; resta com'è, si riguarda con il bordo dello scorrimento); in fondo ai pannelli grandi la
  lente prende l'ombra della lastra (1–2 livelli di serie); un foglio scuro a filo dello schermo aveva in fondo una
  banda di circa 24 px più scura, che alla chiusura della terza fase non c'è più. Tutto il resto di ciò che questa sezione dice dell'aspetto è misurato sul banco
  (`tools/glassbench`), che rende sul PC con Skia gli shader veri dalle scene scritte dalla ricetta; nel profilo di
  serie 22 delle 34 misure con un intervallo di Apple ci stanno dentro e le 12 fuori hanno ognuna la scelta o il limite
  della misura che la spiega (`artifacts/liquid-glass/bench/f1a-chiusura/misure-contro-bersagli.md`). **Non visti**:
  la destra-sinistra di tutto il sistema (con la sola impostazione di sviluppo l'app non si gira: si è misurata solo la
  barra specchiata), la sezione `set` della pagina «Icone», il foglio a schermo intero in chiaro, l'ombra che cresce
  premendo, il fotogramma `Liquid` di una forma che poi passa a `Classic`, il ritaglio al 98% sull'angolo dei grandi
  misurato in numeri, le bande sul gradiente del corpo, i colori su uno schermo a gamma larga, un Android 13 o più
  vecchio vero e un dispositivo diverso dal Tab S9 (l'S25 è della fase S5). Visti dopo il giro di correzione: due
  bagliori insieme (ognuno col suo punto luce), la comparsa con `intensity` (filo e corpo dal primo
  fotogramma) e la pressione di un tasto (± 5 livelli a fotogramma, nessun lampo).
- **Un'app che dipende da una ottica scritta per nascondere lo sfondo ne ottiene meno di prima.** Il pannello del
  lettore di Fluidify (`Modal`, `blurScale` 7) esce a 11,5 dp di smerigliatura contro i 17,5 della 3.x, e la scheda del
  titolo (`Modal` di serie con una pellicola sua) a 4,5 contro 8,75: il materiale nuovo smeriglia un pannello quanto
  un tasto e la crescita oltre il ruolo non basta a riportarla (`tavole/f1a/compatibilita.md`). Una schermata che
  dipende da copertine nascoste può tenere `Classic`; su copertine vere non si è guardato.
- **I tasti di vetro dell'assistente restano alla misura vecchia.** In `engine-ai-ui` il dock ha tre
  `FluidGlassIconButton` con l'icona a 22, 22 e 20 dp, nell'accento; il composer, tondi da 40 dp con l'icona a 20 dp,
  nell'inchiostro del testo salvo l'azione principale (l'invio nell'accento, la fine dell'ascolto in rosso). Le barre
  vicine hanno 26 dp in inchiostro. Nessuna icona Material nei componenti vale per `engine-ui`, `engine-ai-ui`,
  `engine-richtext` e la galleria, non per `engine-wear` (`FluidWearList` tiene `Icons.Rounded.Refresh`).
- **L'ombra di una superficie fisica si vede solo dentro il suo nodo.** Col materiale nuovo l'ombra è nel corpo, e il
  layer del vetro è ritagliato sul nodo: un nodo stretto attorno alla sagoma la taglia. I palchi dell'engine grandi
  quanto la finestra la contengono, e la barra fra le pagine allarga il suo nodo; un'app che mette `fluidPhysicsSurface`
  su un nodo della taglia della forma lo deve allargare (o lasciare la superficie su un palco).
- **Le trasformazioni costano di più della 3.x** (misurato sul Tab S9, `dumpsys gfxinfo` e fotogrammi lunghi dentro i
  viaggi, contro la 3.1.2 nella stessa sessione). Il menu «Ordina» che nasce da un tasto: la stessa quota di fotogrammi
  lunghi (16% contro 18%), tempo mediano del fotogramma 15 ms contro 12, GPU al 90° percentile 9–10 ms contro 4; i menu
  che si espandono appena di più. Molto di più le trasformazioni contestuali: il gruppo di tasti che diventa menu (32%
  di fotogrammi lunghi contro 4%), il menu contestuale (26% di fotogrammi in ritardo per `gfxinfo`, contro 4,7%), la
  card che diventa foglio (26,7% contro 13,1%). La causa: la superficie che viaggia cattura la pagina a piena
  risoluzione, perché luce e contorno stanno nel corpo; a metà risoluzione il gruppo che diventa menu torna al 3,4%, ma
  il filo scatterebbe al passaggio di consegne. È il debito della fase di ottimizzazione. Il costo di una pagina piena
  di vetro e su un'altra GPU non è misurato.
- **Difetti aperti visti sul tablet** (sessione di chiusura della terza fase, e le fasi dopo): il collo breve (16–33 ms)
  quando due pezzi si riuniscono (quello fra due pezzi che si separano dura ora 91–121 ms, e la **banda d'ombra** sopra e
  sotto la vita, che c'era, **è chiusa**: sul tablet da −19…−21 a −2…−4 livelli in scuro, da −9…−14 a −1…−3 in chiaro, e sul
  banco da 4–6,5 livelli a meno di 1 su un tasto); il costo delle trasformazioni
  contestuali (sopra); sul tema scuro il clic lungo su «Azione» che cambia corpo in un fotogramma (il bagliore della
  pressione sparisce al passaggio di consegne, come nella 3.1.2); il bordo doppio di «Ordina» nei primi 110 ms circa;
  una riga debole (1–2 livelli) per un fotogramma quando un tasto diventa due e quando un gruppo diventa menu; la
  pillola della tab bar premuta, che a pressione zero non è identica a quella a riposo (sul banco +7 livelli sul testo
  chiaro, −3 sulla foto; sul tablet +14 in scuro); al bordo della lente della goccia sopra il testo una mezzaluna
  specchiata, dalla piega del campo che al bordo ha pendenza infinita; nella pagina «Parità» l'ombra sotto il pannello
  di Fluid-physics più scura di 12 livelli. **Aperti da ciò che la fase adattiva ha trovato**: l'ombra di un pezzo a
  sfocatura stretta (`glassShadowPiece`: angoli puntiformi sotto mezza sfocatura, errore fino a 0,29 sui tondi grandi e circa
  0,2 con l'ombra corta su un tasto), la parte comune a lente di due pezzi sovrapposti (−4…−6 livelli) e l'ombra della goccia
  (`GlassShadowMaskRing`, stesso rapporto di cadute, non misurata), che vanno a F3.3; la cucitura verticale di un fotogramma
  del collo e i viaggi interrotti o invertiti, non ripresi sul tablet. **Aperti sull'adattività**: i numeri del filtro, i tempi
  del giro e la profondità del vetro girato (74 su 255 contro 43 di Apple) sono ipotesi che si scelgono sulle riprese del
  tablet (S3), non sul banco; i grandi non scuriscono in continuo finché nessun pannello grande segue l'inchiostro; `Clear`
  non ha ancora un componente che lo posi (F3.2); le immagini di riferimento del banco per il tasto tinto di iOS 26 non sono
  rimisurate sullo stato attuale. **Aperti sul bordo dello scorrimento** (se ne occupa il seguito, F2.5b; vedi
  [schermata.md](schermata.md)): in basso la tab bar mostra ancora il contenuto non sfumato; il bordo in basso resta 10 dp
  troppo alto quando la tab bar si piega a 52 dp; la striscia della pagina che esce, tornando indietro, si spegne in tre
  fotogrammi; sulle foto sature una riga di testo si intuisce ancora dietro la metà bassa dei tasti (13,9% di contrasto
  contro l'8% di Apple); la diagnostica conta le strisce fra le superfici `classic`; lo scurimento sul contenuto scuro è
  provato sul banco e non è stato visto sul tablet. Confermati sul tablet nella sessione finale della
  fase: la notifica con la larghezza ferma e un bordo solo (nell'angolo, in uscita, restano due o tre contorni per
  circa 60 ms), l'etichetta e l'icona della scheda scelta col contrasto alto (8,8:1), il segmentato che cambia aspetto
  una volta sola, l'indice di sezione che non fa più scorrere la pagina, il disco del tasto indietro fra due pagine,
  l'ombra del foglio dipinto attorno al foglio. **Non corretta**: l'icona di partenza doppia nella pillola, per un
  fotogramma a ogni cambio di scheda (c'era già nella 3.1.2). In più: alla chiusura del menu contestuale la pagina
  cambia velo in un fotogramma (c'era già nella 3.1.2); l'etichetta sotto la pillola premuta, in scuro, è a 2,9:1.

### Il vetro dipinto

Un foglio (`FluidSheet`) o un avviso (`FluidAlert`) fuori da un `FluidGlassModalHost` stanno in un'altra finestra di
piattaforma, dove non c'è niente da catturare, e il loro vetro è **dipinto**; lo stesso per `fluidStaticGlassSurface`,
che un'app usa in quelle finestre. Dalla 4.0 il dipinto non ha numeri suoi: si calcola dalla ricetta di serie di un
pannello modale (`GlassPaintedProfile.kt`) con le formule degli shader, quindi è lo stesso vetro del materiale nuovo
in tutto ciò che un colore pieno può essere.

- **Il corpo** è il colore pieno che il vetro vero ha sopra la pagina del tema (`colorScheme.background`) sotto lo
  scrim: il velo con il suo ispessimento, la tinta, l'ombreggiatura verso il contorno. Su una pagina uniforme è esatto;
  su una foto o su un testo il vetro vero cambia e il dipinto no, ed è la differenza dichiarata: un dipinto non finge
  una pagina sfocata, e dal foglio non si legge più, nitida, la pagina dietro (prima il contenitore era la superficie al
  92%). Il corpo del foglio lo dipinge il contenitore, che copre anche la maniglia.
- **Il bordo**: sul chiaro il filo scuro sul perimetro e la riga chiara con l'alone in alto e in basso, sullo scuro il
  filo chiaro e il riflesso, le stesse formule della luce su tratti della sagoma rientrata; il bordo del foglio comincia
  alla sua cima, sopra la maniglia.
- **L'ombra** è quella del vetro (`glassShadow`, le due maschere della ricetta).
- **Annidato**: un vetro dipinto dentro un foglio o un avviso dipinti si posa come un vetro annidato (meno velo e meno
  ombra), sopra il corpo del pannello e non sopra la pagina.
- **Lo scrim** dei pannelli dipinti è quello dei modali di vetro: nero al 12% sul chiaro, al 20% sullo scuro
  (`GlassPaintedScrim`, da cui legge anche `FluidGlassModal`). Nella 3.x il foglio di piattaforma aveva un nero al 42%
  e l'avviso il velo della `Dialog`. Un vetro dipinto **sulla pagina** (fuori da un foglio o da un avviso dipinti) non
  ha scrim sotto: si calcola sopra la pagina del tema.

**Quando un'app vede il dipinto.** Solo **fuori da un host**. Il caso normale di un'app dell'engine ha un
`FluidGlassModalHost` alla radice, e lì `FluidSheet` e `FluidAlert` non aprono una finestra: sono i modali di vetro
dell'host, col vetro vero e lo scrim dei modali (12/20%, invariato dalla 3.x). Il foglio di piattaforma
(`ModalBottomSheet`) e la `Dialog` dipinti compaiono in un'app senza host, o per un avviso chiesto con
`inHost = false`; e `fluidStaticGlassSurface` ovunque un'app lo metta. Col materiale nuovo il foglio di piattaforma
passa dal 42% allo scrim unico, l'avviso dal velo della `Dialog` allo scrim unico; con `Classic` o sotto Android 13
tornano il 42% e il velo della `Dialog` (anche se il modello cambia con l'avviso aperto). Nella galleria, che ha l'host
alla radice, il dipinto si vede nella pagina «Vetro dipinto» (`--es gallery.page painted`).

**Sceglie il materiale come ogni altro vetro** (`paintedGlassTakesLiquid`, chiesto da `fluidStaticGlassSurface`,
`FluidSheet` e `FluidAlert`): con `FluidGlassModel.Classic`, sotto Android 13 o con le sorgenti delle lastre rifiutate
il dipinto è **per intero** quello della 3.x (i suoi gradienti, il contenitore del foglio al 92% da cui si legge la
pagina, lo scrim e il velo della `Dialog` di prima, nessuna ombra del vetro). Mai un foglio ibrido. Il dipinto **non
segue il contrasto di sistema**: il corpo è il velo della ricetta, mai la pellicola densa che le lastre prendono sopra
il contrasto standard.

**Come si verifica in un'app.** Nel codice: nessun foglio o dialogo con uno scrim proprio (`grep -n "scrimColor\|setDimAmount"`)
o un fondo traslucido scritto a mano al posto di `fluidStaticGlassSurface`. A schermo: un foglio di piattaforma e un
foglio dell'host abbassano la pagina dello stesso tanto, e il filo del foglio è lo stesso dei tasti; con `Classic` il
foglio è tutto quello della 3.x. *Se non va*: un foglio ibrido (corpo nuovo con l'ombra di prima, o il contrario) vuol
dire che qualcuno ha scritto il dipinto a mano invece di passare da `fluidStaticGlassSurface`.

### Per chi tocca il materiale

Le regole, con il perché e il test che tiene ciascuna, sono in [regole.md](../regole.md): le sezioni della 4.0 «Il
materiale nuovo: …», «Un materiale solo: …», «Il vetro dipinto e lo scrim: …» e «Gli shader del vetro: …». In breve:

- **un materiale solo**: ogni strada del vetro passa da `glassRecipe`, e un profilo derivato (pillola, goccia, dipinto)
  si scrive con `copy` dal profilo che riceve, dichiarando i soli numeri che lo distinguono: `GlassRecipeParityTest`
  legge i tre file e fallisce su un numero nuovo non dichiarato;

- **il vetro di prima non si tocca.** Tutto ciò che segue l'`if` di `glassSurface` è il ripiego e deve restare identico
  al pixel; un cambio d'aspetto lì è un cambio per ogni dispositivo sotto Android 13;
- **la scelta del modello sta in un posto**, `resolveFluidGlassModel`, pura, e ogni sua ragione ha un motivo nella
  diagnostica (`glassModelReason` deve dare la stessa risposta: un test le confronta su tutta la tabella);
- **i numeri di gusto vivono solo nel profilo**, e **le scene del materiale nuovo escono dalla ricetta**, mai da
  uniform ricopiati a mano: un numero ricopiato descrive un'intenzione, non ciò che l'engine disegnerà;
- **una sorgente AGSL nuova si registra** nell'elenco della sonda (`GlassShaderSources`), e se è del materiale nuovo in
  una famiglia del riscaldamento; passa dal lint delle sorgenti (che ferma le parole riservate: `flat` ha tenuto
  Fluid-physics al livello `Balanced` per tutta la 3.x) e da `glassbench.py check` prima di consegnare;
- **la lambda di `record` dipende solo dal proprio layer** (l'ombra nuova lo rispetta, e ha il test che la riesegue), e
  uno shader condiviso si configura subito prima di disegnare, mai prima in un pennello tenuto pronto;
- **nel disegno di una lastra ferma non si alloca**: ricetta, ingressi del disegno e piano del campo riscrivono
  contenitori che la superficie tiene; l'unica cosa che si rifà a ogni cambio di uno stato del corpo è l'ultimo anello
  della catena, perché un `RenderEffect` non si modifica;
- **il margine della cattura resta fermo**: comparsa e intensità passano dalla mescola e dagli uniform, mai dal raggio;
  la qualità scala il raggio, ma il margine si calcola a qualità piena e non si muove;
- **il ritaglio resta hardware**, un rettangolo arrotondato (al 98% per le lastre raccordate): mai un path.

## Come si compone un pannello (cosa vede ogni pixel)

```
 sorgente (registrata)      pannello glassSurface                      ciò che si vede
 ┌────────────────────┐     ┌─────────────────────────────────────┐
 │ corpo della pagina │ ──▶ │ 1 cattura (rigioco nelle mie coord.)│
 │ + sfondo OPACO     │     │ 2 vividezza  saturazione × 1,6      │
 └────────────────────┘     │ 3 sfocatura  2,5 dp × blurScale     │ ──▶ immagine piegata al bordo,
                            │ 4 lente      SDF della mia sagoma   │     tinta, filo di luce,
                            │ 5 tinta · bordo speculare · ombre   │     ombra che stacca
                            └─────────────────────────────────────┘
```

1. **Cattura.** Il pannello rigioca la registrazione della sorgente, traslata di dove sta lui. Una barra e un
   tasto tengono quindi **la stessa immagine**, non due schermate indipendenti.
2. **Vividezza.** `colorControls(saturation = 1 + (vibrancy − 1)·intensità)`: il vetro concentra il colore
   che trasmette, e senza questo passo un pannello su una foto è plastica grigia. `vibrancy` vale 1,6 in tutti
   i ruoli. Cresce con l'intensità, non scatta (una barra che satura un fotogramma prima di sfocare «si annuncia»).
3. **Sfocatura.** Raggio di base × `blurScale` × intensità × qualità, poi la regola della taglia. È la parte
   più cara della catena.
4. **Lente.** Un campo di distanza della sagoma del pannello sposta il campione tanto più quanto più vicino
   al bordo: lo sfondo si **piega** dentro il perimetro. È ciò che identifica il materiale. Serve API 33 (AGSL).
5. **Tinta, bordo, ombre.** Una pellicola (`GlassTint.overlay`), un anello speculare illuminato da un angolo,
   un'ombra esterna che stacca il pannello dalla pagina. L'ombra interna esiste ma nessun ruolo di serie la usa.

Il testo, le icone e i badge appoggiati sopra **non fanno parte del materiale** e restano opachi al 100%.

## Il fondale: `GlassBackdropState` e come si registra una sorgente

**Cos'è.** Un *backdrop* è «ciò che sta dietro il vetro», ridotto a una registrazione: il sottoalbero
disegnato viene registrato in un `GraphicsLayer` e ogni pannello lo rigioca. `GlassBackdropState` è la
maniglia pubblica: espone `blurRadius` (la profondità di base della brina), `supported` (falso sotto API 31)
e `refractionSupported` (falso sotto API 33); la registrazione vera è interna. Non si costruisce a mano.

**Firma essenziale.**

```kotlin
@Composable fun rememberGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState
fun Modifier.glassBackdropSource(state: GlassBackdropState, frozen: () -> Boolean = { false }): Modifier
@Composable fun rememberEmptyGlassBackdrop(blurRadius: Dp = DefaultGlassBlurRadius): GlassBackdropState
@Composable fun rememberCombinedGlassBackdrop(behind: GlassBackdropState, inFront: GlassBackdropState,
                                              blurRadius: Dp = inFront.blurRadius): GlassBackdropState
val LocalGlassBackdrop      // GlassBackdropState? — il vetro che i controlli di questo sottoalbero rifrangono
@Composable fun currentGlassBackdrop(): GlassBackdropState   // LocalGlassBackdrop, o un backdrop vuoto
```

**Uso.** Il caso di un pannello fatto a mano (dal `sample`, `GlassOnGlassDemo.kt`):

```kotlin
val stage = rememberGlassBackdrop()
Box {
  // la sorgente: il contenuto che il vetro mostra, con il SUO sfondo opaco dentro la regione registrata
  Content(Modifier.fillMaxSize().glassBackdropSource(stage))
  // il pannello: fratello disegnato DOPO, mai dentro la sorgente
  Column(
    Modifier.glassSurface(
      state = stage,
      tint = GlassDefaults.modalTint(),
      shape = ContinuousCornerShape(FluidRadius.Sheet),
      role = GlassRole.Modal,
    ),
  ) { /* testo e controlli: opachi, non fanno parte del materiale */ }
}
```

**Comportamento.**
- `glassBackdropSource` va sul **corpo** scorrevole, **mai sull'intera schermata**: altrimenti le barre
  campionano se stesse. Il corpo deve dipingere il proprio sfondo opaco *dentro* la regione registrata: una
  registrazione di testo su trasparenza si rifrange in una macchia, e l'originale nitido ci si vede attraverso.
- Compone la sorgente **offscreen una volta** (`CompositingStrategy.Offscreen`): ogni pannello che la rigioca
  campiona un quadrato di texture invece di rieseguire l'intero flusso di operazioni del corpo (su un tablet
  erano le righe e i glifi ridisegnati ~otto volte per fotogramma: 300 ms di render thread).
- La sorgente si registra **sempre**, anche quando nessun pannello la sta leggendo. È il costo che conta
  (sezione «Il costo»). `frozen` ferma la registrazione mentre risponde `true` (contenuto che scorre, lancio).
- `rememberGlassBackdrop(blurRadius)`: chi passa un raggio proprio **non prende** il mezzo dp in più del
  riferimento 2,5 dp (2.12.0). Il valore è il raggio di base prima del moltiplicatore del ruolo.
- Uno stato `rememberCombinedGlassBackdrop` **non ha un layer proprio**: dice solo quali registrazioni
  impilare, dal fondo in su. `glassBackdropSource(combined)` e `glassContainer(combined)` non fanno niente.
- Senza fondale (`rememberEmptyGlassBackdrop`) un controllo tiene bordo, bagliore e risposta al dito: non è
  un caso d'errore, è un tasto su un fondo opaco che non ha niente da rifrangere.

### I due fondali di una pagina: corpo e canvas

`FluidScreen` registra **due** cose, e il perché è l'unico vincolo che non si aggira.

| | cosa contiene | chi lo legge | si legge da |
|---|---|---|---|
| **corpo** | tutta la lista scorrevole | la **chrome** in piedi *sopra*: barra, tab bar, modali, azioni | `LocalGlassBackdrop` / `currentGlassBackdrop()` |
| **canvas** | solo la lavata di colore e il motivo, dipinti e registrati **prima** della lista | le **card di contenuto** in piedi *dentro* la lista | `LocalFluidCanvasBackdrop` / `currentCanvasBackdrop()` |

Una card che campionasse il corpo campionerebbe una registrazione **che contiene se stessa**: feedback
ottico, l'unico artefatto del vetro che non si nasconde. Il canvas è chiuso prima che la lista esista, quindi
non può contenerne un pixel. `currentCanvasBackdrop()` è `null` quando la schermata non ha canvas, e allora
`FluidCard` e compagni disegnano l'opaco di sempre: chiedere `glass = true` dove non c'è niente da attraversare
non costa e non rompe niente. Dentro un modale o un foglio `LocalFluidCanvasBackdrop` è il materiale del
pannello e `LocalFluidCanvasIsGlass` è `true`: il contenuto sta su un vetro già tinto e già smerigliato, e
prende le ottiche «impilate» (`stackedContentOptics()`, `stackedContentTint()`), più forti, perché un tocco
leggero sarebbe indistinguibile dal niente.

`LocalGlassBackdrop` è `null` fuori da una chrome (pagina piatta, dialog in una finestra propria): i controlli
disegnano allora bordo e risposta al tocco senza fondale da piegare.

**Trappole.**
- Un `glassSurface` *dentro* la `LazyColumn` registrata campiona se stesso. Per un overlay di schermata usa
  lo slot `overlay` di `FluidScreen` (riceve il `GlassBackdropState` giusto) o lascialo opaco.
- Passare ai controlli sempre «lo» stato di pagina (`backdrop = stage`) va bene: dalla 2.13.0 sanno appoggiarsi
  da soli al pannello che li contiene.
- Un corpo senza sfondo opaco registrato dentro: macchia + testo nitido che trapela.

**Non usarlo quando.** Per una card dentro la lista non registrare il corpo: usa il canvas (`glass = true`).

**Versione.** `GlassBackdropState` e `glassBackdropSource` da sempre; `frozen` dalla 1.18; canvas e due
registrazioni dalla 1.5.0; `LocalFluidSurfaceSide` dalla 2.0.

## `glassSurface`: un composable diventa un pannello di vetro

**Cos'è.** L'unica porta d'ingresso del materiale: sceglie ottica (dal ruolo), tinta, bordo, ombre e le
manopole di costo, e le consegna a `drawBackdrop` (vedi [vetro-backdrop.md](vetro-backdrop.md)). Anche
`glassControlSurface` (tasti) e `fluidPhysicsSurface` (superfici che si trasformano) riusano le stesse ottiche
e la stessa formula della taglia, per questo un pannello a riposo e uno in viaggio sono la stessa lastra.

**Firma essenziale** (tutti i parametri: `api/vetro.md`).

```kotlin
@Composable
fun Modifier.glassSurface(
  state: GlassBackdropState,
  tint: GlassTint,
  shape: Shape = RectangleShape,
  edge: GlassEdge = GlassEdge.None,           // None | Bottom | Top: filetto della tinta dove tocca il contenuto
  falloff: GlassFalloff = GlassFalloff.Uniform, // Uniform | FadeDown (barra sotto la barra di stato)
  intensity: () -> Float = { 1f },            // 0 = vetro assente, 1 = materiale pieno
  role: GlassRole = if (falloff == GlassFalloff.FadeDown) GlassRole.Bar else GlassRole.Floating,
  optics: GlassOptics = GlassDefaults.optics(role),
  opticalDepth: () -> Float = { 1f },         // peso su lente, bordo e ombre; non su sfocatura e tinta
  pressed: () -> Float = { 0f },              // 0..1: approfondisce la lente senza ri-registrare
  exports: GlassBackdropState? = null,        // pubblica il materiale di questo pannello per altro vetro
  layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
  sampleOnce: Boolean = role == GlassRole.Content,
  resampleIntervalMillis: Long = 0L,
  frozen: () -> Boolean = { false },
  heldWhileMoving: () -> Boolean = { false },
  nestInContainer: Boolean = true,
): Modifier
```

**Comportamento.**
- **Senza `RenderEffect` (API < 31)** non c'è sfocatura né lente né vividezza: campionare lo sfondo darebbe
  alla barra una copia *nitida* della pagina, peggio che non campionarla. `glassSurface` ritaglia la sagoma e
  dipinge `GlassTint.fallback` (con lo stesso `edge`/`falloff`/`intensity`): un materiale definito e quasi
  opaco, non una trasparenza rotta. Su **API 31–32** restano vividezza e sfocatura, la lente no
  (`refractionSupported == false`).
- **Una sagoma che la lente non sa descrivere** perde la rifrazione e tiene sfocatura e vividezza (a monte
  sarebbe un'eccezione). `RectangleShape` vale «raggi zero»; le forme con angoli (`CornerBasedShape`,
  quindi anche `ContinuousCornerShape`) vanno tutte. Nel materiale nuovo una sagoma così resta `Classic`.
- `intensity` fa **arrivare il materiale come materiale** (salgono vividezza, sfocatura, lente, pellicola, bordo e
  ombra; nel materiale nuovo la smerigliatura sale per mescola, a raggio fermo), invece di un rettangolo finito che
  sfuma. Ma **il contenuto del pannello si dissolve con lui**: con valori frazionari e falloff `Uniform` il pannello
  compone materiale e contenuto in un `saveLayer` alla stessa alfa (`amount` ≥ 0,999: disegna e basta; ≤ 0,001: non
  disegna niente, contenuto compreso); `FadeDown` usa sempre il suo `saveLayer` con la maschera; il materiale nuovo
  dissolve il materiale da sé e il contenuto in un `saveLayer` suo, solo a metà strada. Un'etichetta che deve restare
  piena mentre il vetro arriva sta quindi **fuori** dal pannello, in un fratello disegnato dopo (la barra di
  `FluidScreen` fa così con titolo e azioni). Solo il ripiego sotto API 31 lascia pieno il contenuto. Su una
  **superficie grande** del vetro di prima meglio animare l'`alpha` del risultato finito; vedi «Il costo».
- `FadeDown`: forza massima in alto, a zero in basso, per una barra sotto la barra di stato — il bordo duro
  di una barra uniforme è la cosa più artificiale di una barra traslucida. Il pannello intero (vetro, tinta,
  bordo) sfuma insieme con una maschera `DstIn` **fuori** dal renderer (dentro verrebbe sfocata anch'essa e
  la rampa avrebbe un labbro morbido); la tinta finisce prima (da 0,55 dell'altezza) per non lasciare un
  labbro grigio. Con falloff `Uniform` e intensità piena non c'è nessun buffer in più.
- `opticalDepth` e `pressed`: un indicatore a riposo fonde il bordo; `GlassOptics.edgeFloor` tiene un minimo
  di bordo e ombra a profondità zero (altrimenti la sagoma scompare su un fondo chiaro).
- `layerBlock` trasforma l'intero pannello; il renderer inverte la trasformazione quando campiona, quindi
  la lente ha in mano lo sfondo che c'è davvero dietro. Con un `layerBlock` la scala di cattura resta 1.
- **Il bordo ha un solo colore per lato** (`glassRimRecipe`, interna): sul lato scuro è luce, bianco al 50%
  in `Plus`; sul chiaro è una riga **scura** (nero al 28%) in `SrcOver`. Girare il solo colore compila,
  gira e non disegna niente: un bordo scuro in `Plus` si somma a niente. Il profilo è quello dello shader:
  pieno dove la sagoma guarda in su o in giù, zero ai fianchi.
- **L'ombra esterna è del vetro**: nero al 16% sul chiaro, al 10% sullo scuro (`glassShadowColor`), per il peso
  del ruolo. Sul chiaro ombra e filetto sono *tutto* ciò che dice che la superficie galleggia; sullo scuro un'ombra
  nera su nero non fa niente e alza la pellicola.
- **Chiaro o scuro si decide in un solo punto**: `GlassDefaults.isDarkSurface()`. Legge `LocalFluidSurfaceSide`
  e, se è `null`, la luminanza di `colorScheme.surface`. La palette può mentire in un solo modo: un design
  fatto di *film* traslucidi mette il film in `surface` (un bianco al 10%), e `luminance()` ignora l'alfa: una
  app nera risulta chiara e ogni pannello prende il ramo sbagliato (barra che schiarisce una pagina già
  bianca). `FluidTheme` fornisce già `LocalFluidSurfaceSide`; chi ha un tema proprio lo dice una volta.

**Trappole.**
- `glassSurface` ha un nodo di vetro proprio: stare **dopo** la sorgente e non dentro di essa.
- Un pannello con `exports` pubblica **se stesso** (materiale, tinta e tutto): serve per far piegare a un
  controllo la barra e non la pagina sotto. Gli `exports` di uno scrim registrano a forza **piena**, perché il
  suo arrivo è un alpha: chi campiona quel composito resta scuro mentre lo scrim sfuma. Meglio campionare la
  pagina cruda e dipingersi la propria quota di scurimento.
- Il primo e l'ultimo fotogramma di vita di un nodo possono disegnare con i valori di default: su un pannello
  che arriva per alpha è un lampo a piena forza (misurato: un fotogramma a 190 di luminanza in una serie a
  214). Cancello: `drawWithContent { if (amount > 0.004f) drawContent() }`.
- Una `GlassOptics` scritta a mano passa da `sanitized()`: `blurScale` fra 0 e 16, `vibrancy` fra 0 e 3
  (non finito → 1), `pressedDepthBoost` fra 0 e 2, `backdropResolution` fra 0,1 e 1, ogni altro valore non
  finito è riportato a un valore sicuro. Un'altezza di rifrazione negativa fa campionare lo shader fuori
  dal proprio input e una alfa NaN avvelena tutta la catena: per questo si corregge, non si propaga.

**Non usarlo quando.** Per un tasto usa `glassControlSurface`; per una sagoma che deve trasformarsi,
`fluidPhysicsSurface` (schede Controlli e Fluid-physics): lì si disegna il vetro in viaggio.

**Versione.** Dalla 1.4.0; `exports` dalla 1.4.0; `frozen`/`heldWhileMoving` dalla 2.10.0; `nestInContainer`
dalla 2.13.0; la regola della taglia dalla 2.12.0; il materiale nuovo (`Liquid`) dietro la stessa firma dalla 4.0.

## I ruoli: `GlassRole` e cosa riceve ognuno

`GlassRole` dice il **lavoro ottico** di un pannello; il colore è un'altra faccenda (`GlassTint`). Cinque ruoli,
e `GlassDefaults.optics(role)` restituisce per ciascuno un'istanza stabile e senza allocazioni. Numeri veri,
dal sorgente (`GlassMaterial.kt`), col riferimento di serie di 2,5 dp:

| ruolo | `blurScale` → raggio base | lente (altezza / spostamento) | cupola · dispersione | bordo (larg. · alfa) | ombra esterna (raggio · peso) | spinta da pressione | note |
|---|---|---|---|---|---|---|---|
| `Bar` | 5 → **12,5 dp** | 20 / 16 dp | no · no | 0,8 dp · 0,5 | nessuna | 0 | lo spostamento è *minore* dell'altezza: una barra è un foglio largo e basso, e uno spostamento forte farebbe ondeggiare il tetto dello schermo mentre sotto scorre una lista |
| `Floating` | 1,8 → **4,5 dp** | 19 / 29 dp | no · no | 0,8 · 0,6 | 20 dp · 0,7 | 0,35 | i numeri della capsula di Kyant: lo spostamento *oltre* l'altezza fa leggere un bordo spesso, il contrario (che aveva) è plastica |
| `Interactive` | 1,4 → **3,5 dp** | 10 / 14 dp | **sì** · **sì** | 0,8 · 0,55 | 12 dp · 0,5 | 0,6 | quasi chiaro: è la lente a identificarlo. Spostamento < 24 dp: una lente da 44 dp che ne sposta 24 si tira dentro ciò che è a un centimetro e si legge come un alone colorato |
| `Modal` | 3,5 → **8,75 dp** | 20 / 28 dp | no · no | 0,8 · 0,6 | 32 dp · 0,8 | 0 | fogli e avvisi che arrivano su una pagina ancora nitida |
| `Content` | 0,8 → **2 dp** | 18 / 22 dp | no · no | 0,8 · 0,5 | **nessuna** | 0 | `backdropResolution` 0,5; `sampleOnce` di serie |

In tutti: `vibrancy` 1,6, ombra interna 0 (nessun ruolo la usa), bordo a 90°.

- **`Bar`** — chrome da bordo a bordo, quieto. Raggio base ×5 perché è **l'unica** superficie con testo nitido
  che le scorre sotto da una pagina che non controlla: andare chiaro qui fu un errore, la cima di ogni
  schermata «perdeva». Non diventa però un riempimento: passati ~8 dp la pagina si media a un solo colore e
  non c'è più niente da rifrangere. È il ruolo di default quando `falloff = FadeDown`. Niente ombra: la barra è
  a filo con lo schermo.
- **`Floating`** — la capsula di navigazione, la superficie che si guarda più a lungo: trattamento pieno (smusso
  visibile, bordo lucido, ombra che separa da ciò che scorre sotto). Ruolo di default di `glassSurface` negli
  altri casi. L'usano `FluidFloatingTabBar`, `FluidFloatingCapsule`, `FluidSidebar`.
- **`Interactive`** — il controllo che cede sotto il dito e porta la lente più forte; cupola (centro che
  «gonfia») giusta solo su una superficie abbastanza piccola da leggersi come una lente (sotto ~40 dp è
  sbagliata anche lì); dispersione cromatica accesa — è l'unico posto abbastanza piccolo perché sette campioni per
  pixel valgano il costo. `glassControlSurface` lo usa con `buttonOptics()`; ne leggono anche la lente
  dell'indice di sezione e i tasti della barra di pagina. Chi non porta testo azzera la sfocatura al sito di chiamata.
- **`Modal`** — più profondo di una barra, più calmo di un controllo. Il 3,5 (e non 6) è voluto: a 6 la pagina
  dietro un avviso è un colore e l'avviso smette di stare *davanti* a qualcosa. Un modale **con scrim** non ne ha
  bisogno e lo dice: lo scrim stesso usa `Modal` con `blurScale = 1,5`, lente a zero, `sizeResponse = 0`, tinta
  nera al 20% (scuro) o 12% (chiaro), ripiego nero al 92%.
- **`Content`** — card e gruppi lista *nella* pagina, non sopra. È l'unico ruolo senza ombra, e non per
  sobrietà: il vetro di contenuto sta alla stessa profondità del testo accanto, e quando due card di una lista
  dicono entrambe «galleggio» la pagina diventa una pila di lastre sciolte. Tiene uno smusso corto e netto —
  un bordo, non una cupola, perché a quella taglia il bordo è tutto. Il raggio sta basso perché ciò che c'è
  dietro è una lavata già morbida: sfocarla di nuovo non compra niente e costa l'unica cosa che vale, il suo
  gradiente che si piega nel perimetro. Una cupola su 300 dp pieni di testo farebbe nuotare il paragrafo.

**Le ottiche derivate** (`GlassDefaults`):

| funzione | cosa è |
|---|---|
| `buttonOptics()` | `Interactive` con altezza 12 dp e `pressedDepthBoost = 24/14 − 1`: a riposo la lente di sempre (10 / 14 dp), **premuto** quella di LibrePods (12 dp di altezza, spostamento che sale da 14 a 24 dp). Solo `glassControlSurface` la usa di serie (3.0) |
| `stackedContentOptics()` | contenuto su **un altro pannello**: sfocatura 1,8 (4,5 dp), lente 14 / 18, bordo 0,6, `backdropResolution` 0,6. Tutti i numeri salgono, non scendono: il vetro di sotto è già tinto e smerigliato |
| `panelOptics(compact, overSharpPage)` | il pannello flottante. Base: sfocatura 2,5 (6,25 dp), lente 14 / 18, bordo 0,8 dp al 45%, **nessuna ombra interna**, ombra 18 dp al 36%. `compact`: lente 7 / 9. `overSharpPage`: sfocatura 3,5 (2,8 se anche compatto) |

**Un materiale per i pannelli (3.0).** Popover, menu contestuale, finestra che si espande, foglio, notifica,
callout, bolla dell'indice, menu che si trasforma: **un pannello flottante è un pannello flottante**.
Prima ognuno aveva i suoi numeri (la finestra sfocava 2,2 con un bordo da 1,1 dp, il popover 2,5 con 0,8,
la notifica e il callout la lente di `Modal`): messi accanto erano cinque vetri per la stessa famiglia e
nessuno era sbagliato da solo, era la somma che si leggeva. Se il tuo pannello non è nessuno di questi,
è il caso non previsto e si discute; se lo è, non si ritocca un numero nel suo file.
- **La lente è una misura assoluta, il pannello no.** 18 dp di spostamento su una finestra larga sono un
  bordo; sugli stessi 18 dp di un menu di due righe sono un terzo del pannello, e l'immagine ripiegata sul
  margine si stacca da quella dritta al centro: si vedono **due versioni** dello stesso oggetto nello stesso
  vetro. Per questo `compact` per un menu di poche righe.
- **Sopra una pagina nitida sale solo la sfocatura** (`overSharpPage`): il popover ha lo scrim che sfoca la
  pagina prima di lui, la notifica e il callout no. Non oltre ciò che la lente copre, perché il padding della
  cattura vale `max(0, sfocatura − altezza)` e deve restare zero in un viaggio di Fluid-physics.
- **Niente ombra interna insieme a un bordo speculare**: sono due trattamenti dello stesso millimetro di
  perimetro, e insieme si leggono come due bordi invece che come spessore.

## L'ottica: spessore, lente, sfocatura, dispersione, bordo

`GlassOptics` descrive «quanto è spesso il vetro» in termini fisici, in `Dp`, e tutte le distanze riguardano
il **bordo**, perché è lì che il vetro lavora.

| campo | significato | note vere |
|---|---|---|
| `blurScale` | moltiplica `GlassBackdropState.blurRadius` | il raggio finale è `base × blurScale × intensità × qualità`, poi la regola della taglia |
| `refractionHeight` | quanto in dentro dal perimetro si sente ancora la piega | altezza grande + spostamento piccolo = cupola dolce; altezza corta + spostamento grande = smusso netto. Apple sta intorno a altezza ≈ spostamento |
| `refractionAmount` | quanto lontano lungo la normale è trascinato il campione al bordo | cresce con la pressione: `× (1 + pressione × pressedDepthBoost)` |
| `depthEffect` | aggiunge un termine diretto al centro alla normale: la smusso diventa cupola | giusto su un controllo piccolo; su una barra larga farebbe nuotare il centro dello schermo |
| `dispersion` | separa il campione rifratto in sette lunghezze d'onda | solo su una superficie piccola, spessa e in movimento: su una grande costa sette campioni per pixel e si legge come una frangia. Si spegne sotto qualità 0,75 |
| `vibrancy` | moltiplicatore di saturazione | 1 = spento |
| `highlightWidth` · `Alpha` · `Angle` | l'anello speculare | angolo in gradi, orario dall'asse x positivo: da dove viene la luce (90° = dall'alto) |
| `innerShadowRadius` · `Alpha` | l'ombra interna, lo spessore visto dall'interno | tutti i ruoli di serie la tengono a zero |
| `shadowRadius` · `Alpha` | l'ombra esterna | |
| `pressedDepthBoost` | spostamento extra col dito giù, frazione di `refractionAmount` | |
| `edgeFloor` | quanto di bordo e ombra sopravvive a profondità ottica zero | zero (default) = comportamento di sempre |
| `backdropResolution` | frazione di risoluzione a cui la superficie elabora il fondale, *sopra* quella guidata dalla sfocatura | tenerla a 1 per tutto ciò che sta su **contenuto** (testo sotto una barra a mezza risoluzione è sbavato) |
| `sizeResponse` | quanto la regola della taglia vale per questa ottica (0..1) | 0 per lo scrim; default 1, aggiunto dopo la 2.11.0 |

**Perché «la leggibilità si compra col raggio, non con l'opacità».** Un'etichetta sul vetro si legge se ciò
che c'è dietro è *nascosto*, e ci sono due modi: nasconderlo (sfocatura) o coprirlo (tinta opaca). Coprirlo
trasforma il vetro in un riempimento grigio, e il materiale smette di esistere. Quindi la tinta resta bassa
(vedi tabella sotto: 30–56% di alfa per le pellicole di materiale) e il lavoro lo fa il raggio, dove serve: 12,5 dp sulla barra (l'unica con testo
nitido che le scorre sotto), 8,75 sul modale, 2 su una card sopra una lavata già morbida. Il principio ha però
un **tetto da due parti**, ed è la lezione dei giri di taratura — il raggio di riferimento è andato 8 → 16 →
2 → 2,5 dp, e il giro di mezzo è l'errore da non rifare: sopra circa **8 dp** un pannello smette di
trasmettere un'immagine e trasmette la sua *media*, e un pannello che tiene una media è un riempimento; tutto
il lavoro della lente sul bordo sta allora piegando un colore piatto in un altro colore piatto. L'ordine
delle ragioni: (1) il materiale è la **dislocazione** al perimetro e il filo di luce, non la brina — quindi il
raggio di base resta minuscolo e sale la lente; (2) le poche superfici che devono davvero nascondere
qualcosa chiedono un multiplo con `blurScale`; (3) la taglia può alzarlo fino a un certo punto (sezione
seguente) e non oltre. Il 2,5 dp è una taratura **a occhio**, chiesta così, calibrata anche contro il
catalogo di Kyant e contro Square, che atterra sullo stesso 2 dp. Il mezzo dp in più è il vetro «un filo più
smerigliato».

**Il film del vetro non è `MaterialTheme.surface`.** Una barra dello stesso colore del fondo non è un materiale
traslucido, è niente: su un tema AMOLED spariva. Il Liquid Glass è un materiale *chiaro e riflettente* che anche
in scuro si legge come un pannello più chiaro del fondo. `glassFilm()` (interna) parte da due grigi fissi —
`0xFFFAFAFA` sul chiaro, `0xFF4A4A4E` sullo scuro, quelli di Square, arrivati a mano contro lo stesso renderer — e
prende solo il **34%** della palette (`surfaceContainerLowest` sul chiaro, `surfaceContainerHigh` sullo scuro),
quanto basta per seguire l'accento senza che un tema AMOLED lo trascini giù fino al fondo da cui deve staccarsi.

## La regola della taglia (2.12.0)

Un pannello grande smerigliato come uno piccolo sembra un pannello piccolo ingrandito: il bordo piega l'immagine
di pochi dp su una superficie larga mezzo schermo, e lo spessore non si legge più. Quindi più il **lato corto**
cresce, più sfoca e più rifrange. La formula è una (`glassSizedOptics`), usata da `glassSurface` e da
`fluidPhysicsSurface`; nel morph conta la taglia della *sagoma*, non quella del layer che la ospita.

- **Guadagno** = smoothstep fra **64 dp** (zero esatto: è l'altezza della pillola di navigazione, che deve
  restare com'è; «zero», non «quasi zero») e **320 dp** (una finestra di alert, un gruppo lista a larghezza
  di telefono; sopra non cresce più). La smoothstep parte con pendenza zero: 64,0001 dp non fa uno scalino.
- **Al guadagno pieno**: sfocatura **×2**, altezza di rifrazione **×1,6** (mai oltre mezzo lato corto, dove lo
  shader satura; mai sotto l'altezza di base), spostamento al bordo **×1,8**. Con il riferimento di 2,5 dp:
  `Content` arriva a 4 dp, `Floating` a 9, `Modal` a 17,5; a 320 dp `Floating` ha 30,4 dp di altezza e 52,2 di
  spostamento, `Modal` 32 e 50,4 (`GlassSizeRuleTest` lo fissa).
- Questo **tende** la regola di sopra (oltre 8 dp si trasmette la media): è voluto per i pannelli grandi (il
  catalogo di Kyant tiene una finestra da 250–300 dp a 16 e 8 dp), ma oltre il doppio sarebbe l'errore del 16 dp
  un'altra volta. I tre massimi sono numeri di partenza **da giudicare su un dispositivo**, non verità; lo
  spostamento è il più delicato: se cresce più dell'altezza si stacca dall'immagine dritta e fa vedere due
  versioni dello stesso oggetto (il difetto «fisheye»).
- **Due vincoli che non si toccano.** (1) La sfocatura non cresce oltre la lente a profondità piena che la copre:
  il padding della cattura vale `max(0, sfocatura − altezza)` e deve restare zero anche mentre una sagoma
  cambia taglia (un padding che si muove sporca la cattura a ogni fotogramma). (2) **Sotto API 33, senza lente,
  la regola è spenta.** Il tetto `blurScale` 16 vale anche per il prodotto finale.
- Si esce con `GlassOptics.sizeResponse = 0`: lo scrim dei modali copre lo schermo senza essere un pannello
  grande, e sfocato fino alla media farebbe l'opposto del suo mestiere.

## La tinta: `GlassTint` e le pellicole di `GlassDefaults`

`GlassTint(overlay, fallback, hairline)`: `overlay` è dipinto **sopra** il fondale rifratto («tienilo basso: il
materiale deve trasmettere»), `fallback` sta al posto del fondale **sotto API 31**, `hairline` è il filetto sul
lato che tocca il contenuto (con `GlassEdge.Top`/`Bottom`: spesso 0,5 dp, minimo 1 px). Le funzioni di
`GlassDefaults` sono `@Composable` (leggono il tema); tranne `selectionTint`, tutte passano da
`forSystemContrast()`.

Nel materiale nuovo `overlay` conta secondo il quarto campo, `mode` (sezione «Due materiali», «Le tinte»): le
pellicole di famiglia qui sotto sono `Neutral`, e il loro `overlay` lo dipinge solo il vetro di prima (nel nuovo il
velo è del profilo); `tintedModalTint` e `panelTint(colour)` sono `Colored`; `selectionTint` e le pellicole sopra
una foto sono `Fixed`, e si dipingono com'erano. `fallback` e `hairline` valgono come prima in tutti e due. Alfa dal
sorgente (scuro / chiaro):

| pellicola | per cosa | `overlay` | `fallback` | `hairline` |
|---|---|---|---|---|
| `barTint()` | barre | film 0,40 / 0,44 | `surface` 0,95 / 0,96 | `onSurface` 0,16 / 0,10 |
| `floatingTint()` | navigazione flottante (viaggia su contenuto arbitrario: un po' più densa) | film 0,48 / 0,52 | `surface` 0,96 / 0,97 | `onSurface` 0,18 / 0,08 |
| `controlTint()` | controlli: quasi niente, ma tinta d'accento (film con il **20%** di `primary`, perché un bianco al 18% era di fatto niente e i tasti uscirono trasparenti con l'etichetta illeggibile) | 0,34 / 0,38 | `surface` 0,88 / 0,90 | trasparente |
| `selectionTint()` | la capsula che marca una scelta in una barra: appena percettibile, la scelta è già portata dall'accento della scheda sotto | bianco 0,12 / nero 0,08 | bianco 0,16 / nero 0,10 | trasparente |
| `contentTint()` | card e liste: l'unico punto in cui il film può tornare verso la superficie dell'app (regge paragrafi: lavata + film + testo sono un livello di troppo) | mix `surfaceContainerLow`/film (40%) 0,52 / 0,56 | `surfaceContainerLow` opaca | trasparente |
| `stackedContentTint()` | contenuto su un altro vetro: più densa di una card di pagina | film 0,30 / 0,34 | `surfaceContainerLow` | trasparente |
| `modalTint()` | fogli e avvisi | film 0,50 / 0,54 | `surface` 0,97 / 0,98 | `onSurface` 0,14 / 0,08 |
| `tintedModalTint(colour)` | una finestra che porta il colore della cosa da cui nasce | `lerp(base, colour, 0,78)`, alfa 0,72 / 0,76 | `lerp(base, colour, 0,82)`, alfa 0,97 / 0,98 | `lerp(base, colour, 0,30)` |
| `panelTint(colour?)` | la pellicola dei pannelli di `panelOptics` | `modalTint()` o `tintedModalTint(colour)` | | |

Il ripiego del **modale tinto** prende il colore quasi pieno (82%) perché sotto API 31 non c'è immagine da
trasmettere; la tinta no (78%): sopra questa quota il vetro smette di trasmettere e diventa un rettangolo colorato.
Il perché del 78: un menu contestuale si apre spesso sopra un'altra superficie satura, e un film timido perdeva
contro il colore che aveva dietro, tanto da sembrare non applicato. **Perché tingere il materiale e non la card
dentro**: una card colorata che si apre in una finestra neutra diventa una card dentro una card — due bordi,
due superfici — e il colore, che era l'informazione, finisce relegato in un rettangolo interno.

**Sopra una fotografia.** Il film di famiglia è un materiale *chiaro* per costruzione: anche in scuro schiarisce,
ed è ciò che fa leggere la barra come vetro su una pagina piatta. Sopra una copertina a tutto schermo fa il
contrario del suo mestiere (alza il pavimento proprio dove i controlli devono restare leggibili). Per questo
le tinte «sulla foto» sono **fisse e non derivate** (uno scrim non ha una palette da cui prendere, e derivarle le
farebbe ripassare dalla stessa rilevazione chiaro/scuro che una `surface` traslucida può ingannare):

| pellicola | `overlay` | `fallback` | `hairline` |
|---|---|---|---|
| `darkBarTint()` | nero 0,42 | `0xFF101012` 0,95 | bianco 0,12 |
| `darkFloatingTint()` | nero 0,48 | `0xFF141416` 0,95 | bianco 0,16 |
| `lightBarTint()` | bianco 0,56 | `0xFFFBFBFD` 0,96 | nero 0,16 |
| `lightFloatingTint()` | bianco 0,64 | `0xFFFDFDFF` 0,96 | nero 0,20 |

`barTintOnPhoto()` / `floatingTintOnPhoto()` scelgono il braccio dal lato (`isDarkSurface()`). Il braccio chiaro
è più denso del gemello speculare perché l'inchiostro scuro ha bisogno di più foto tolta di quanto ne serva alla
luce (le parti chiare di una foto sono molto più luminose di qualunque lavata scura, e sono loro a inghiottire
le lettere scure): nero 0,42 risponde a bianco 0,56. Misurato: per un'app sul chiaro sopra una copertina scura la tinta «di famiglia» dava
3,7:1 di contrasto (inchiostro scuro su grigio medio, sotto soglia), le tinte `…OnPhoto` 5,7:1. Il filetto si gira insieme (su una copertina
**chiara** l'overlay non ha più niente da fare e il filetto è la sola cosa che dice che una barra c'è). **Si usa
tutta la famiglia o niente**: una app che scurisce la barra e lascia la pillola ha costruito la giunzione che
voleva nascondere (`FluidFloatingTabBar`, la barra e la finestra che ne nasce devono essere lo stesso materiale).
`darkBarTint`/`darkFloatingTint` sono il braccio scuro e non guardano il lato dell'app (dalla 2.4.0 esiste il chiaro).

## Il contrasto del testo sul vetro

Il vetro è fatto per trasmettere, e la trasparenza è la prima cosa che un utente che chiede più contrasto
non vuole: un'etichetta su una pellicola che lascia passare una foto si legge male a chiunque, malissimo a lui.
Il testo sul vetro si tiene leggibile con quattro leve, in quest'ordine:

1. **Il testo non è vetro.** Etichette, icone e badge restano opachi al 100%; il materiale è il contenitore.
2. **Il raggio** nasconde ciò che sta sotto (sezione «L'ottica»); non l'opacità.
3. **La pellicola** è la leva «locale»: per questo la tinta di una barra è più densa di quella di un controllo.
4. **Il contrasto di sistema** (Android 14+, Impostazioni › Accessibilità › Contrasto del colore): la pellicola e il
   ripiego salgono verso il pieno, il bordo si fa più netto.

**Cos'è.** `LocalFluidSystemContrast` (0 standard, 0,5 medio, 1 alto) lo mette `FluidTheme` leggendolo da
`UiModeManager.getContrast()` (`rememberFluidSystemContrast()`, che **si aggiorna quando l'utente lo cambia** con
l'app aperta; 0 prima di Android 14 e dove il servizio manca). `GlassTint.forContrast(c)` / `forSystemContrast()`
rendono la pellicola più densa; le tinte di `GlassDefaults` lo fanno da sole, una tinta scritta a mano **no**
(e neppure `selectionTint()`).

**Numeri veri** (`FluidSystemContrast`, fissati in `FluidSystemContrastTest`):
- `veil(c)`: 0 a standard, **0,40 a medio**, **0,85 ad alto**, lineare fra uno e l'altro. Valori fuori scala o non finiti
  si riportano ai livelli (`level`).
- `overlay` e `fallback`: `a' = a + (1 − a)·veil` — una barra al 44% sale al 66% a medio e al 92% ad alto. Non
  il 100%: un pannello del tutto pieno smette di dire «vetro» e si scambia per una finestra rotta.
- `hairline`: fino a **×2,5** ad alto (a medio la parte che gli spetta), **mai oltre 0,7**, e uno che già lo supera non si
  abbassa; un bordo **trasparente resta trasparente** (una pellicola che non lo vuole non lo riceve).
- Il **colore** della pellicola non cambia: è lo stesso grigio, solo che copre di più. A standard `forContrast` torna
  la stessa istanza, bit per bit.
- **Il modo diventa `Fixed`** (4.0), qualunque fosse: nel materiale nuovo, col contrasto alzato, il vetro dipinge la
  pellicola densa di prima al posto del velo, e il testo si legge quanto sul vetro di prima (misurato sul banco: lo
  stesso rapporto al centro della lastra, fino al 5% in meno sul bordo di un'icona). A contrasto standard il materiale
  nuovo usa il suo velo, e sopra un fondo del tono opposto al tema si legge peggio di prima: sezione «Due materiali»,
  «I limiti dichiarati oggi».

**Trappole.**
- Una tinta nuova di un pannello nuovo deve passare da `forSystemContrast()` (tutte quelle di `GlassDefaults` lo
  fanno) e **una sola volta**: `tintedModalTint` parte da una base a contrasto standard proprio per applicarlo alla
  fine; partendo da `modalTint()` lo applicherebbe due volte su ripiego e bordo, e la tinta, che ricalcola da capo,
  non lo vedrebbe.
- Per vederlo senza toccare le impostazioni: `CompositionLocalProvider(LocalFluidSystemContrast provides 1f)` dentro il tema
  (la galleria lo fa).
- Il contrasto non fa un contrasto garantito: sotto una foto sensibile resta compito di chi sceglie la pellicola
  (`…OnPhoto`) e il lato (`LocalFluidSurfaceSide`).

**L'inchiostro che sta sul vetro.** Due pezzi di aritmetica di colori, interni ma utili da conoscere perché
spiegano un comportamento: il riempimento di una barra di avanzamento (`FluidProgressBar`) si porta, **del minimo
che serve**, verso il nero o il bianco per fare almeno **3:1** (WCAG 1.4.11) con la pista su pagina, card, card più alta
e sul film del vetro (`FluidFillContrast.kt`); l'icona del puck premuto nella barra flottante fa lo stesso contro
la pellicola del pannello (`legibleInk`, 3:1). Misurato: alcuni accenti facevano 1,7:1 con la pista, e una barra al 60% non si
distingueva da una al 100%. L'accento resta quello se già basta; altrimenti resta riconoscibile come lo stesso colore.

## Qualità adattiva: `FluidGlassQuality`

**Cos'è.** Il costo del vetro e la sua leggibilità non arrivano nello stesso momento: il vetro si guarda a pagina
ferma; durante uno scorrimento veloce nessuna lente è visibile ma il conto si paga per intero, ed è l'unico momento
in cui un fotogramma perso si vede. Quindi la qualità (0..1) è una funzione della **velocità** di scorrimento.

**Firma essenziale.**

```kotlin
@Composable fun rememberFluidGlassQuality(ceiling: () -> Float = { 1f }): FluidGlassQuality
fun fluidGlassQualityScrollConnection(quality: FluidGlassQuality): NestedScrollConnection
val LocalFluidGlassQuality   // FluidGlassQuality? — null = «nessuno lo guida», vale 1 fisso
@Composable fun currentGlassQuality(): Float
```

**Uso.** `FluidScreen` lo fa da solo (`LocalFluidGlassQuality` + un `nestedScroll` sul corpo): un'app che usa
`FluidScreen` non scrive niente. In una schermata propria: `val q = rememberFluidGlassQuality()`, il connection
su un `nestedScroll` del corpo, e `CompositionLocalProvider(LocalFluidGlassQuality provides q)`.

**Comportamento** (numeri dal sorgente):
- Sotto `FullQualityVelocity` = **24 px per fotogramma** (circa mezzo centimetro a 60 Hz: si legge mentre si scorre)
  il materiale è intero; sopra `MinQualityVelocity` = **140** è a `ScrollingFloor` = **0,25**; in mezzo, lineare. Non zero:
  a zero il vetro *sparisce*, e una superficie che appare e scompare col gesto si nota molto più di una che si assottiglia.
- Misura **pixel per fotogramma**, non pixel al secondo: su un dispositivo che già perde fotogrammi la distanza cresce da
  sola, e la difesa si stringe proprio quando serve.
- **Scende subito, risale piano** (recupera il 12% della distanza per fotogramma): scendere in fretta non si nota perché la
  pagina si muove; risalire in fretta sì, perché la pagina si è appena fermata ed è lì che si guarda. Fine del lancio: torna
  intero di colpo. Un lancio **comincia** veloce: il connection abbassa già in `onPreFling`.
- **Cosa scala**: lente e suoi extra (dislocazione, dispersione, ombre, bordo speculare) — la *forma* del materiale.
  **Non scala la tinta** (tiene leggibile il testo: un testo che perde contrasto mentre la lista scorre è peggio di qualunque
  fotogramma in ritardo). La **sfocatura scala poco**: da 1 a `FluidGlassBlurFloor` 0,45 sulla stessa rampa, quindi a pavimento
  resta ~59% del raggio. La dispersione si spegne sotto 0,75. Sotto **0,7** la cattura scende a **metà risoluzione** (un quarto
  dei pixel nella catena: la leva più grande che il materiale abbia), quantizzata a due soli valori perché cambiare la risoluzione
  *è* ri-catturare: due ricatture per lancio invece di sessanta.
- **Tetto da fuori** (`ceiling`): il livello è il **minimo** fra scorrimento e tetto. `FluidScreen` lo aggancia al movimento fra
  pagine: mentre due pagine si attraversano esistono due fondali e due serie di pannelli, e il vetro resta al pavimento
  (un timbro da 40 ms che scade da solo, perché nessuna delle due pagine sa quando l'altra ha finito).

**Trappole.** Un `LocalFluidGlassQuality` assente non è un errore: vale 1. La qualità è letta come *stato* dentro le
lambda di disegno, non catturata in composizione: un cambio di livello ridisegna e basta, non ricompone.

**Versione.** Dalla 1.6.0; il KDoc diceva «la sfocatura resta intatta»: scende fino al 45% (corretto nella 2.12.0).

## Vetro su vetro (2.13.0)

Un tasto su un pop-up, una card su un foglio, il pomello di un interruttore su una card di vetro: quasi ogni app ha un vetro
appoggiato a un altro vetro. Il vetro di sopra campiona il **materiale** di quello di sotto — il fondale come lo vede lui,
sfocato, rifratto e tinto — e ci aggiunge la propria ottica: **non è mai più nitido di quello su cui sta.** Prima, una app passava
ai controlli l'unico stato che aveva in mano (quello della pagina) e un tasto da 3,5 dp apriva dentro un pannello da quattro
o cinque volte tanto una finestra nitida sulla pagina, più chiara dello scrim intorno (misurato: le righe del fondo passavano
con un'ampiezza più di tre volte quella del pannello).

- **Lo trova l'albero, non la composizione.** Ogni nodo di vetro cerca il nodo di vetro più vicino fra gli antenati e ne campiona
  il materiale (un modificatore non può dare un `CompositionLocal` al contenuto; un nodo può essere trovato da un nodo figlio).
  Vale quindi anche per un pannello che l'app si fa con `glassSurface` su un suo `Box`.
- **Va nella pila al posto giusto**: sopra tutto ciò che lo stato contiene e sta *fuori* dal pannello (la pagina dietro il pop-up),
  sotto la prima cosa registrata *dentro* (la pista di un interruttore).
- **Un cablaggio a mano vince**: se lo stato contiene già il materiale di quel pannello (`exports` + `rememberCombinedGlassBackdrop`:
  le azioni della barra, la chiusura di una notifica, l'indicatore della tab bar) non cambia niente.
- **Il materiale esclude il contenuto**: dentro c'è il vetro che lo campiona, e un tasto che rifrange se stesso è feedback ottico.
- **Costa zero finché dentro non c'è vetro.** Il materiale si registra solo mentre qualcuno lo campiona e solo se è cambiato
  (geometria, o uno stato che i suoi disegni hanno letto). A schermo fermo `FluidGlassDiagnostics.totalExports` non si muove.
- **Le ottiche si sommano da sole**: due sfocature gaussiane fanno una sfocatura di raggio √(r₁² + r₂²); un tasto da 3,5 dp su un
  pannello da 14 finisce a 14,4: non diventa una macchia, e la sua identità la portano bordo, lente, tinta e ombra, che restano.

```kotlin
// Il contenuto NON è figlio del vetro su cui sta (una superficie che si trasforma, un fratello):
val pane = rememberGlassBackdrop()
Box(Modifier.fillMaxSize().fluidPhysicsSurface(state, backdrop, tint, exports = pane))
Column(Modifier.glassContainer(pane)) { FluidGlassButton(text = "Attiva", onClick = {}) }

// Il pannello lo disegna l'app con un altro motore: un nodo con SOLO il materiale, poi il contenuto.
Box(modifier) {
  Box(Modifier.matchParentSize().glassMaterialSource(pane).liquidGlass(/* nocheck: il motore dell'app */))
  Column(Modifier.glassContainer(pane)) { /* vetro dell'engine */ }
}
```

`glassContainer(material)`: il vetro dentro questo nodo sta su `material` — uno stato di `rememberGlassBackdrop`, **non uno combinato**.
`glassMaterialSource(state)`: registra, **solo** finché del vetro lo campiona, ciò che disegnano i modificatori che *seguono*; va su un
nodo che porta solo il materiale, mai il contenuto (finirebbe nel proprio fondale). A differenza di `glassBackdropSource` registra solo su
richiesta e non passa da un layer offscreen. **Due eccezioni nominate**: `nestInContainer = false` per chi ricostruisce da sé il
materiale del pannello su cui sta (il puck della barra flottante e la riga nascosta che guarda: sfocano come la barra partendo dalla
pagina cruda; altrimenti sfocherebbero due volte, difetto della 2.12.1); e il contenuto che non è figlio (sopra).

## Il costo

Ogni superficie di vetro costa **una registrazione di layer più una catena di `RenderEffect` sui propri limiti**, pagata per pixel; la
sfocatura è la parte più cara. Sei regole, nell'ordine in cui mordono.

**1. Il vetro va sul contenitore, mai sulla riga.** Un `FluidListGroup` di dodici righe è **un** nodo di vetro, non dodici. Mettere il materiale un livello troppo
in basso porta una schermata da otto superfici a ottanta e fa cadere fotogrammi per qualcosa che nessuno può vedere. Testo, icone e badge sopra restano opachi.
`glass = true` è una **richiesta**: senza canvas o sotto API 31 il componente disegna l'opaco di sempre. Il vetro nel contenuto vuole **due** cose: un canvas ambientale
(il vetro sopra il grigio è invisibile per costruzione: una lente su una pagina piatta produce una pagina piatta, e la sua assenza si legge come un bug) e due registrazioni (sezione «I due fondali»).

**2. Un pannello di vetro è una texture, e una texture ha un tetto.** Ogni superficie registra un `GraphicsLayer` grande quanto sé, cioè una texture GPU. Oltre il tetto la
registrazione non fallisce rumorosamente: torna **vuota**, e sotto una tinta traslucida è un rettangolo nero. La superficie che ci arriva è sempre un gruppo lista (alto
quanto le righe che ha). Dalla 1.5.1 il renderer lo vede da solo (`fitToTexture`): il lato lungo della cattura è tenuto sotto **4096 px** (metà degli 8192 che sono il minimo
hardware da anni; il padding si aggiunge sopra, e non c'è modo di leggere il limite vero da un `DrawScope`) scendendo di scala, mai sotto **0,05**, e mai *salendo* oltre
ciò che il chiamante aveva chiesto. Ciò che sta dietro un pannello di contenuto è una lavata morbida: a un quarto della risoluzione non perde niente. Anche i layer
ausiliari (anello speculare e ombra interna) sono limitati: oltre **1024 px** di lato lungo si registrano più piccoli. Il **contenuto** del pannello non passa più da un
secondo layer offscreen (1.5.3: la pila è tutta `SrcOver`; sul tablet 85 → 27 ms per fotogramma). Niente di tutto questo è un permesso per gruppi infiniti:
`fluidGlassGroups` spezza una lista lunga in pannelli da otto righe.

**3. Quando il pannello ricattura.** Solo quando la cattura **non può più essere quella giusta**: l'elemento è cambiato, la dimensione registrata cambia, o (senza
`sampleOnce`) la **posizione relativa alle sorgenti** è cambiata — un pannello e il suo fondale che si muovono insieme (una pagina trascinata di lato con il suo vetro) hanno
esattamente la cattura che avevano. Una sorgente che si ridisegna non invalida nessun pannello: il display list tiene i figli per riferimento, quindi ogni pannello vede già
il contenuto nuovo. Solo il **padding** della catena invalida (non i numeri degli effetti): una sfocatura che sale con lo scorrimento non costa altro che ricostruire l'effetto.

**4. Le manopole di costo di `glassSurface`.**
- `sampleOnce` — campiona **una volta** e tiene la cattura mentre il pannello si muove. Di serie `true` solo per `GlassRole.Content` (sfondo = lavata ambientale statica: un gradiente
  che viaggia col pannello è indistinguibile da uno fisso, ed è l'intero costo per fotogramma del vetro di contenuto: su un tablet erano centinaia di ms di render thread per fotogramma).
  **Mai per vetro sopra contenuto vivo**: una barra su un corpo che scorre porterebbe in giro una copia vecchia della pagina. Una cattura **vuota** (coordinate non ancora agganciate)
  non conta: `sampleOnce` aspetta la prima valida (1.22: una tessera montata nel fotogramma sbagliato restava un rettangolo di tinta per sempre).
- `resampleIntervalMillis` — un tetto alla frequenza di ricattura. 0 = «quando serve», che sopra uno sfondo animato è **a ogni fotogramma**. Su un Galaxy S25: 44 ms per fotogramma con una
  cattura ciascuno, 61 ms con nove vive (1.22.0); sotto una sfocatura larga una cattura vecchia di 100 ms non si distingue, e costa un dodicesimo.
- `frozen` — finché `true` tiene la cattura che ha, **ma la rinfresca comunque ogni 110 ms** (`HoldRefreshMillis`): una cattura congelata per tutta un'animazione è un riflesso da correggere alla fine, e quella
  correzione è un salto visibile. Uscire da un congelamento riprende sempre una cattura fresca. È il caso di una barra che si piega (misurato: 9 fotogrammi da 250–450 ms contro 77 da 42 ms col
  congelamento). Tiene solo se il pannello è ancora dov'era; `heldWhileMoving` tiene anche mentre si muove (per una sagoma che cambia forma: un riflesso in ritardo per la durata di un morph non lo vede nessuno,
  togliere il vetro e rimetterlo sì). Non è `sampleOnce`: quello parla di uno sfondo che non si muove, questo di **limiti** del pannello che si animano.
- `glassBackdropSource(frozen = …)` — la stessa cosa dal lato sorgente: registrare costa una traversata dell'intero sottoalbero (scala con le operazioni di disegno, non con i pixel): una
  lista lanciata paga ogni riga a ogni fotogramma. Il prezzo è un riflesso che smette di seguire mentre il contenuto scorre; sotto la sfocatura è quasi invisibile.
- `backdropResolution` (ottica) e la scala **guidata dalla sfocatura**: la risoluzione di cattura è `1 − t·0,6` con `t = raggio_dp / 10` (a un raggio ≥ 10 dp il pannello lavora al **40%**), per ruolo
  a 2,5 dp di base: `Bar` 0,40, `Floating` 0,73, `Modal` 0,48, `Content` 0,88 × 0,5 di `backdropResolution`. Un pannello quasi chiaro resta a piena risoluzione perché la sua lente *è* tutto bordo e un bordo
  morbido è l'unica cosa che non può avere. Il pavimento è prudente: più giù il raggio si schiaccia sotto il pixel e l'upscale non ha più niente che lo nasconda (pixelatura, non brina).

**5. Le sorgenti a schermo intero sempre registrate sono il costo che oggi non ha manopola.** `glassBackdropSource` registra sempre, anche se **niente** la sta campionando; la 2.13.0 ha reso pigri
solo i materiali che i pannelli *pubblicano* (`exports`, vetro su vetro). Misure Perfetto sull'S25 fatte dall'app MyCloud Gallery (13 ottobre 2026, fuori da questo repo): nel pinch e nello scorrimento
della griglia il collo di bottiglia è il **RenderThread**, 7–10 ms per fotogramma e 47 passate GPU fra i fondali di vetro a schermo intero (lista → pagina → radice) e le sfocature di barra, segmenti e pillola;
i picchi da 20–36 ms sono `vkAllocateMemory` (3–5 ms) dopo decine di `vkFreeMemory`, cioè la cache delle risorse di Skia sotto pressione. Togliere un `glassBackdropSource` a schermo intero
sempre acceso alla radice (registrarlo solo mentre un pop-up è a schermo) ha ridotto i picchi. **Regola pratica**: prima di aggiungere un livello di vetro a schermo intero, chiediti **chi lo legge**.
Registrare una sorgente solo mentre ha lettori, come gli export, **non esiste ancora** (candidato per una 2.14, mai arrivato nella 3.0.4: il codice di `LayerBackdropNode` registra sempre); si misura su un
dispositivo vero con Perfetto, non su un emulatore.

**6. «Livello più basso».** Due usi della parola, entrambi sul sorgente:
- il vetro **di livello più basso** è `glassSurface` rispetto ai componenti che lo avvolgono: `FluidCard(sampleOnce, resampleIntervalMillis)` (3.0.4) espone le stesse due manopole «come il vetro di livello più
  basso», di serie `true` e `0`. Se la card sta su uno sfondo che si muove, si abbassa la frequenza lì, non si riscrive la card;
- «mettere il materiale **un livello troppo in basso**» è l'errore della regola 1 (vetro sulla riga invece che sul contenitore).
Un terzo significato, `FluidGlassQuality.level`, è la qualità adattiva della sezione precedente: «livello» lì è sempre quel numero 0..1.

**Misurare.** `FluidGlassDiagnostics.enabled = true` in una build di debug: `totalCaptures` (ricatture dei pannelli) e `totalExports` (materiali pubblicati) devono **smettere di muoversi a schermo fermo**. La galleria
lo registra su `adb logcat -s FluidGlassDiag` una riga ogni volta che i numeri cambiano. Spento costa una lettura volatile per cattura.

**Una regola per chi anima.** Per far *arrivare* un pannello si anima l'`alpha` del risultato finito, o `intensity` perché il materiale arrivi come materiale; non si scala mai un layer di vetro.
`intensity` dissolve anche il contenuto del pannello (sezione «`glassSurface`»): i controlli che devono restare opachi mentre il vetro arriva stanno fuori dal pannello, fratelli disegnati dopo.
Il consiglio storico («non animare `intensity` su una superficie grande: il raggio decide il padding e il padding invalida la cattura») resta vero, nel vetro di prima, dove la lente non copre la sfocatura (API 31–32, ottiche scritte a mano
con più sfocatura che altezza); con le ottiche di serie e la lente accesa il padding resta zero a ogni intensità. Nel materiale nuovo `intensity` non tocca il raggio, quindi il margine della cattura resta fermo a ogni intensità.
**Contare nel materiale nuovo.** Oltre alle catture, `FluidGlassDiagnostics` conta le superfici per materiale, i nodi e i layer vivi, e dice a che punto è il riscaldamento: sezione «Due materiali», «La diagnostica».

## Token, costanti, `GlassDefaults`

- `DefaultGlassBlurRadius` = **2,5 dp**: raggio di riferimento, per i pannelli *piccoli*; i grandi ne pagano di più per la regola della taglia. Solo per il vetro di prima: il materiale nuovo non lo legge.
- `FluidGlassModel` (`Liquid`, `Classic`) e `LocalFluidGlassModel` (di serie `Liquid`, una richiesta); `GlassTintMode` (`Fixed`, il default; `Neutral`; `Colored`).
- Interne del materiale nuovo, da conoscere: il profilo `GlassLiquidProfile` (i numeri di gusto, con le varianti), `glassRecipe`, le soglie degli attenuatori (`GlassLiquidFullBlurScale` 0,8, `GlassLiquidFullRefractionHeightDp` 7, `GlassLiquidFullRefractionAmountDp` 9,
  `GlassLiquidFullHighlightAlpha` 0,45, `GlassLiquidFullShadowAlpha` 0,36), il tetto della sfocatura chiesta in più (`GlassLiquidFrostRequestMax` 3), il velo scuro dei grandi (`GlassLiquidDarkLargeStartDp` 48, `GlassLiquidDarkLargeEndDp` 96), i piccolissimi (`GlassLiquidTinyBelowDp` 44, `GlassLiquidTinyReflexFloor` 0,35 da 20 dp, `GlassLiquidTinyReflexDepthFraction` 0,16), `GlassLiquidMinCaptureScale` 0,4, `GlassSmoothedClipScale` 0,98, `GlassWarmUpQuietMillis` 120.
- `GlassDefaults`: `optics(role)`, `buttonOptics()`, `stackedContentOptics()`, `panelOptics(compact, overSharpPage)`; le tinte della tabella (`barTint`, `darkBarTint`, `floatingTint`,
  `darkFloatingTint`, `lightBarTint`, `lightFloatingTint`, `barTintOnPhoto`, `floatingTintOnPhoto`, `controlTint`, `selectionTint`, `contentTint`, `stackedContentTint`, `modalTint`,
  `tintedModalTint`, `panelTint`); `isDarkSurface()` (pubblico dalla 2.10.0, per gli effetti che si disegnano *sopra* il vetro e devono girare col materiale come il suo bordo).
- `FluidSystemContrast`: `Standard` 0, `Medium` 0,5, `High` 1, `level(raw)`, `veil(contrast)`.
- `FluidGlassQuality`: `FullQualityVelocity` 24, `MinQualityVelocity` 140, `ScrollingFloor` 0,25.
- `GlassEdge` (`None`, `Bottom`, `Top`) e `GlassFalloff` (`Uniform`, `FadeDown`).
- `LocalGlassBackdrop`, `LocalFluidCanvasBackdrop`, `LocalFluidCanvasIsGlass`, `LocalFluidSurfaceSide`, `LocalFluidSystemContrast`, `LocalFluidGlassQuality`.
- Interne ma da conoscere: la regola della taglia (`GlassSizeSmallDp` 64, `GlassSizeLargeDp` 320, `GlassSizeBlurBoost` 1,0, `GlassSizeHeightBoost` 0,6, `GlassSizeAmountBoost` 0,8, `GlassMaxBlurScale` 16),
  `GlassMinResolutionScale` 0,4, `GlassFullQualityBlurDp` 10, `FluidGlassBlurFloor` 0,45.

## Cosa è verificato dai test

- `GlassMaterialStateTest`: l'ordine dei cinque ruoli (barra quieta ma pesante, controllo il meno sfocato, `Content` senza ombra e a risoluzione ridotta), `sanitized()` che
  corregge ogni valore malformato ed è identità sui preset, `intensity` malformata = assente, il lato dell'app indovinato dalla luminanza finché l'app non lo dice, l'indicatore che tiene il bordo a riposo.
- `GlassSizeRuleTest` e `FluidGlassSizeTest` (la seconda parla di taglie di tasto, non della regola): il guadagno è esattamente zero sotto i 64 dp, smoothstep, i tre massimi, i numeri del CHANGELOG, il padding a zero a ogni taglia,
  la regola spenta senza lente, `sizeResponse`, base 2,5 dp ovunque sia scritta.
- `GlassPanelMaterialTest` (base = quella del popover, `compact` cambia solo la lente, `overSharpPage` solo la sfocatura, una istanza stabile per combinazione); `GlassRimRecipeTest` (il bordo per lato, il giro nelle sue due metà,
  la rampa contro lo shader, il colore dell'ombra).
- `FluidGlassQualityTest` (rampa monotona, scende subito e risale piano, il tetto esterno), `FluidSystemContrastTest` (livelli, velo, bordo, nessun muro), `FluidFillContrastTest`.
- `GlassNestingTest` (la pila, dove va il materiale, cablaggio a mano lasciato stare, conto dei lettori mai negativo), `BackdropTextureCapTest` (tetto di texture, due soli valori di scala), `SourceGoneHoldTest`.
- Il materiale nuovo, sul JVM: `GlassModelSelectionTest` (la tabella di verità della scelta, la forma → piano, i due spazi di corpo e luce, il margine della cattura fermo, la luce in due disegni, il ritaglio al 98%, l'ombra che non monta dove il ruolo non la vuole),
  `GlassRecipeTest` (il filo che non cresce, smerigliatura e ombra che crescono, `intensity` a raggio fermo, ogni ottica di serie lo stesso vetro, l'ottica scritta a mano che chiede meno e ottiene meno, le varianti del profilo),
  `GlassDrawParityTest` (ciò che l'innesto passa agli shader è ciò che le scene scrivono, uniform per uniform, su tasto, capsula, tab bar, menu e pannello, chiaro e scuro, due densità), `GlassSceneExportTest` (le scene scritte dalla ricetta),
  `GlassTintModeTest` e `GlassDefaultsTest` (i tre modi, la tinta col colore cambiato, il contrasto alzato, le ottiche e le pellicole di prima invariate), `GlassCompatSignaturesTest` (le chiamate che le app usano compilano, e una tinta a tre argomenti resta la pellicola dipinta),
  `GlassFieldGeometryTest` e `ContinuousCornerFieldTest` (il campo e l'angolo continuo), `GlassShadowTest` (l'ombra a due maschere, anche rieseguita), `GlassWarmUpTest` (il riscaldamento), `ShaderSourceLintTest` (le sorgenti).
  Le strade del materiale (F1B): `GlassRecipeParityTest` (una superficie fisica ferma = la `glassSurface` della stessa forma, su corpo, luce e ombra, entro un decimillesimo di dp; i capi di un viaggio = la superficie ferma; i profili derivati seguono quello di serie e i loro numeri propri sono un elenco chiuso; la tabella di verità modello × livello di API × livello di Fluid-physics per le cinque strade; nessuna prova saltata: il dipinto sceglie il modello e il primo fotogramma di un viaggio ha il velo della superficie ferma),
  `GlassPuckProfileTest`, `GlassDropProfileTest`, `GlassPaintedProfileTest` (ogni profilo derivato, contro le misure di Apple sul banco), `PhysicsLiquidTest` (Fluid-physics sul materiale nuovo).
  Nella galleria, in Robolectric: `GlassPathSelectionTest` (il percorso a ogni livello di API, il riscaldamento, il livello simulato) e `GlassNodeCountTest` (4 nodi e 3 layer, 3 e 2 senza ombra; il vetro di prima 5 nodi e 5 layer, di cui 3 dedotti dal codice dei nodi di Kyant che la diagnostica non conta). Sul dispositivo: `GlassShaderCompileInstrumentedTest`, `GlassNodeCountInstrumentedTest` (letture: `Liquid` 4 e 3, `Classic` 5 nodi e 2 layer, `Liquid` senza ombra 3 e 2) e `GlassNestingInstrumentedTest` (quattro alberi con il vetro dentro il vetro, composti davvero, nei due temi, con la regola dell'annidato accesa e spenta; più il fumetto di chat). Dopo la review, sul JVM: `GlassFieldOutlineTest` (il contorno del campo contro `Shape.createOutline`, le forme che restano `Classic` per un angolo oltre mezzo lato corto, il fumetto a due altezze, le forme ad angoli uguali mai misurate), `GlassNestedOnPanelTest` (la cattura che apre sempre con un'operazione, il pannello pieno che pubblica il suo colore, il materiale che resta pubblicato finché un pannello lo pubblica) e `GlassLiquidTraceExportTest` (la traccia del tocco col materiale nuovo).
- **Non verificato dai test**: com'è il vetro. Gli host dei test non hanno fondale; sfocatura, lente, bordo e ombra si guardano in mano (dimensione D1 di `docs/10-coerenza.md`), e le tre costanti della taglia sono da giudicare su un dispositivo. Il materiale nuovo è misurato sul banco (`tools/glassbench`) e **visto su un solo dispositivo, un Tab S9** (vedi «I limiti dichiarati oggi»).

## Dove guardare

- `engine-ui/…/ui/fluid/GlassMaterial.kt`: `GlassBackdropState`, sorgenti e fondali, `GlassTint`, `GlassRole`, `GlassOptics`, `GlassDefaults` (tutte le ottiche e le tinte, con la storia dei numeri nei commenti), `glassSurface`,
  la regola della taglia, `glassShadow`, il ripiego.
- `…/ui/fluid/GlassContrast.kt` (contrasto di sistema), `FluidGlassQuality.kt` (qualità adattiva), `FluidFillContrast.kt` (riempimento a 3:1), `…/theme/FluidTheme.kt` (fornisce lato e contrasto).
- `…/ui/theme/FluidComponents.kt` (`fluidContentGlass`: come una card chiede il vetro), `…/ui/fluid/FluidScreen.kt` (le due registrazioni e la qualità), `FluidAmbientSurface.kt`.
- Il materiale nuovo: `…/ui/fluid/FluidGlassModel.kt` (i due modelli e la scelta), il ramo `glassLiquidSurface` in `GlassMaterial.kt`, e `…/ui/glass/field/`: `GlassLiquidProfile.kt` (i numeri di gusto e le loro fonti), `GlassRecipe.kt` (dal profilo ai numeri di una lastra),
  `GlassFieldEffects.kt` (la catena, i due disegni della luce, la superficie), `GlassFieldPlan.kt` (forma → piano, il ritaglio), `GlassFieldKernel.kt` e `GlassFieldGeometry.kt` (il campo e i suoi specchi in Kotlin), `GlassBodyShader.kt`, `GlassLightShader.kt`, `GlassShadow.kt`,
  `GlassFieldHealth.kt` (la sonda e l'elenco delle sorgenti), `GlassWarmUp.kt` (il riscaldamento). Il banco che rende le scene: `tools/glassbench/README.md`.
- La libreria sotto, con gli effetti, il bordo, le ombre, il vetro su vetro e le goccie: [vetro-backdrop.md](vetro-backdrop.md). Il perché lungo di ogni regola: [../regole.md](../regole.md) e `docs/03-design-system.md`, `docs/10-coerenza.md`.
- Esempi: `sample/…/GlassOnGlassDemo.kt` (pannello + controlli, pannello di un altro motore), `catalog/AllGlassDemo.kt` (palco con testo nitido dietro), `GalleryActivity.kt` (diagnostica).
