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
| un tasto o un cursore su un pannello di vetro | niente: dalla 2.13.0 il vetro dentro un altro vetro ne campiona il materiale da solo |
| un pannello disegnato con **un altro motore** che deve ospitare vetro dell'engine | `glassMaterialSource(state)` sul nodo che porta solo il materiale + `glassContainer(state)` sul contenuto |
| un contenuto che **non è figlio** del vetro su cui sta (finestra che si trasforma) | `exports = state` sulla superficie, `glassContainer(state)` sul contenuto |
| dire all'engine da che parte sta l'app (chiaro/scuro) quando la palette mente | `LocalFluidSurfaceSide` (`FluidTheme` lo fornisce già) |
| sapere se la risposta del vetro è «intera» ora | `currentGlassQuality()` |
| un pannello sopra uno sfondo che si muove, troppo caro | `resampleIntervalMillis`, `frozen`, `heldWhileMoving` (sezione «Il costo») |
| vetro su ogni riga di una lista | **non così**: un `FluidListGroup` è un nodo, non dodici |
| un'ombra sotto il vetro | **non così** `Modifier.shadow`: trapela dentro la sagoma. Il vetro disegna la propria; `glassShadow` (interna) per un antenato che ritaglia |
| una goccia (pomello, pillola) con `GlassRole.Interactive` | **non così**: la goccia non legge i ruoli (`glass/interaction/GlassDrop.kt`), vedi [vetro-backdrop.md](vetro-backdrop.md) |
| scalare un nodo di vetro per farlo «rimbalzare» | **non così**: il layer scalato rifrange ciò che non c'è più. La trasformazione sta nel `layerBlock` di `glassSurface` |

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
  quindi anche `ContinuousCornerShape`) vanno tutte.
- `intensity` è il modo per far comparire il materiale lasciando **opachi** i controlli che ci stanno sopra
  (si anima questo, non l'alpha della barra). Con valori frazionari e falloff `Uniform` il pannello compone in un `saveLayer`
  (`amount` ≥ 0,999: disegna e basta; ≤ 0,001: non disegna niente); `FadeDown` usa sempre il suo `saveLayer` con la maschera. Su una **superficie grande**
  meglio animare l'`alpha` del risultato finito; vedi «Il costo».
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
dalla 2.13.0; la regola della taglia dalla 2.12.0.

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
`forSystemContrast()`. Alfa dal sorgente (scuro / chiaro):

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

**Una regola per chi anima.** Per far *arrivare* un pannello si anima l'`alpha` del risultato finito, o `intensity` quando sotto ci sono controlli che devono restare opachi; non si scala mai un layer di vetro.
Il consiglio storico («non animare `intensity` su una superficie grande: il raggio decide il padding e il padding invalida la cattura») resta vero dove la lente non copre la sfocatura (API 31–32, ottiche scritte a mano
con più sfocatura che altezza); con le ottiche di serie e la lente accesa il padding resta zero a ogni intensità.

## Token, costanti, `GlassDefaults`

- `DefaultGlassBlurRadius` = **2,5 dp**: raggio di riferimento, per i pannelli *piccoli*; i grandi ne pagano di più per la regola della taglia.
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
- **Non verificato dai test**: com'è il vetro. Gli host dei test non hanno fondale; sfocatura, lente, bordo e ombra si guardano in mano (dimensione D1 di `docs/10-coerenza.md`), e le tre costanti della taglia sono da giudicare su un dispositivo.

## Dove guardare

- `engine-ui/…/ui/fluid/GlassMaterial.kt`: `GlassBackdropState`, sorgenti e fondali, `GlassTint`, `GlassRole`, `GlassOptics`, `GlassDefaults` (tutte le ottiche e le tinte, con la storia dei numeri nei commenti), `glassSurface`,
  la regola della taglia, `glassShadow`, il ripiego.
- `…/ui/fluid/GlassContrast.kt` (contrasto di sistema), `FluidGlassQuality.kt` (qualità adattiva), `FluidFillContrast.kt` (riempimento a 3:1), `…/theme/FluidTheme.kt` (fornisce lato e contrasto).
- `…/ui/theme/FluidComponents.kt` (`fluidContentGlass`: come una card chiede il vetro), `…/ui/fluid/FluidScreen.kt` (le due registrazioni e la qualità), `FluidAmbientSurface.kt`.
- La libreria sotto, con gli effetti, il bordo, le ombre, il vetro su vetro e le goccie: [vetro-backdrop.md](vetro-backdrop.md). Il perché lungo di ogni regola: [../regole.md](../regole.md) e `docs/03-design-system.md`, `docs/10-coerenza.md`.
- Esempi: `sample/…/GlassOnGlassDemo.kt` (pannello + controlli, pannello di un altro motore), `catalog/AllGlassDemo.kt` (palco con testo nitido dietro), `GalleryActivity.kt` (diagnostica).
