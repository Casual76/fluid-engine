# API · Fluid-physics

_Generato sull'engine 3.0.4 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluidphysics/FluidForm.kt`

- `data class FluidCornerRadii(val topLeft: Float, val topRight: Float, val bottomRight: Float, val bottomLeft: Float)` — I quattro raggi di uno [FluidForm.Slab], in pixel assoluti.
  - `companion object`
    - `val Zero`
    - `fun all(radius: Float): FluidCornerRadii`
- `sealed interface FluidForm` — Una silhouette che Fluid-physics sa tenere ferma o far viaggiare verso un'altra.
  - `val frame: Rect` — Il rettangolo che la silhouette occupa dentro la superficie.
  - `data class Slab(override val frame: Rect, val cornerRadii: FluidCornerRadii = FluidCornerRadii.Zero, val smoothing: Float = ContinuousCornerShape.IosSmoothing) : FluidForm` — Famiglia A: tutto quello che un campo di distanza a raggi d'angolo sa dire — rettangolo, quadrato, cerchio, capsula, angolo continuo.
  - `data class Poly(override val frame: Rect, val vertices: List<Offset>, val rounding: Float = 0f) : FluidForm` — Famiglia B: una sagoma chiusa qualsiasi — un trapezio, una stella, uno scarabocchio disegnato col dito.
  - `data class Group(val pieces: List<FluidForm>, val blendRadius: Float = 48f) : FluidForm` — Fino a [MaxPieces] silhouette rese come **una** superficie liquida: pezzi vicini si versano l'uno nell'altro con un ponte di materiale, ed è l'effetto-firma di due tasti che si fondono in un pannello.
    - `companion object`
  - `companion object`
    - `fun circle(center: Offset, radius: Float): Slab` — Un cerchio perfetto: la forma di riposo del Playground.
    - `fun slabOf(shape: CornerBasedShape, frame: Rect, density: Density, layoutDirection: LayoutDirection): Slab` — Legge una [CornerBasedShape] — comprese le forme di [dev.antigravity.fluidengine.ui.fluid .FluidRadius] — in uno [Slab] posato su [frame], così un componente entra nel motore con la silhouette che aveva già.

## `engine-ui/ui/fluidphysics/FluidFormPresets.kt`

- `object FluidFormPresets` — Il catalogo di forme del Playground, e la porta d'ingresso per quelle disegnate a mano.
  - `const val MaxVertices: Int` — Il tetto dei vertici, ed è il numero dell'array uniform nello shader poligonale: oltre questo, il campo di distanza non li sa portare. I preset stanno molto sotto; il tratto a mano libera ci si ricampiona dentro.
  - `const val FreehandVertices: Int` — A quanti vertici si ricampiona un tratto a mano libera. Abbastanza da tenere le anse di uno scarabocchio vero, abbastanza pochi da lasciare respiro al loop dello shader.
  - `fun square(frame: Rect, radius: Float = 0f): FluidForm.Slab` — Un quadrato centrato nel frame, col lato del lato corto.
  - `fun rectangle(frame: Rect, radius: Float = 0f): FluidForm.Slab`
  - `fun capsule(frame: Rect): FluidForm.Slab` — Una capsula: il raggio è metà del lato corto per definizione, mai un numero fisso.
  - `fun trapezoid(frame: Rect, topInsetFraction: Float = 0.22f, rounding: Float = 12f): FluidForm.Poly` — Un trapezio isoscele: il lato corto sopra, rientrato di [topInsetFraction] per parte.
  - `fun star(frame: Rect, points: Int = 5, innerRatio: Float = 0.45f, rounding: Float = 8f): FluidForm.Poly` — Una stella a [points] punte. [innerRatio] è il raggio delle valli rispetto alle punte.
  - `fun blob(frame: Rect, seed: Int = 0, irregularity: Float = 0.35f, rounding: Float = 10f): FluidForm.Poly` — Una macchia organica, deterministica per [seed]: lo stesso numero dà sempre la stessa macchia, perché una forma che cambia sotto le dita a ogni ricomposizione sembra un bug, non natura.
  - `fun fromFreehand(points: List<Offset>, rounding: Float = 6f): FluidForm` — Un tratto a mano libera reso trasformabile: semplificato (Ramer–Douglas–Peucker), chiuso, ricampionato a [FreehandVertices] passi uguali di perimetro, col verso normalizzato.

## `engine-ui/ui/fluidphysics/FluidMorphMenu.kt`

- `class FluidMorphMenuState internal constructor()` — Il tasto che SI ESPANDE nel proprio menù contestuale — la prima integrazione vera di Fluid-physics nell'interfaccia.
  - `var isOpen by mutableStateOf(false)` — Vero dal gesto d'apertura alla richiesta di congedo.
  - `var isOnScreen by mutableStateOf(false)` — Vero finché c'è qualcosa in scena, viaggio di ritorno compreso: l'ancora resta nascosta.
  - `fun dismiss()` — Chiede il viaggio di ritorno. Il tasto riappare quando la superficie è tornata capsula.
  - `fun open(bounds: Rect, text: String?, icon: ImageVector?, items: List<FluidContextAction>, content: (@Composable () -> Unit)? = null)` — Apre il menu dalla sagoma di [bounds] (pixel della radice). Il gesto e' affare del chiamante: un tasto il cui UNICO mestiere e' il menu puo' aprirlo al tocco — pubblico dalla 1.13.0 per quello (dalla 3.0 [FluidMorphMenuButton] lo fa da se', con `opensOnTap`). Chi chiama da un'ancora propria deve anche nascondersi mentre [isOnScreen] e' vero, come fa quella di casa.
- `@Composable fun rememberFluidMorphMenuState(): FluidMorphMenuState`
- `val LocalFluidMorphMenuState: ProvidableCompositionLocal<FluidMorphMenuState?>` — Lo stato del menu che si trasforma dell'app, per chi sta lontano dalla radice (3.0.1).
- `@Composable fun FluidMorphMenuButton(state: FluidMorphMenuState = LocalFluidMorphMenuState.current ?: rememberFlu…, actions: () -> List<FluidContextAction>, onClick: () -> Unit, modifier: Modifier = Modifier, text: String? = null, icon: ImageVector? = null, backdrop: GlassBackdropState, enabled: Boolean = true, opensOnTap: Boolean = false)` — L'ancora: una capsula di vetro coi gesti dei tasti — tocco per l'azione, tenuto premuto diventa il menù (un `Open`, uno solo, e il dito passa al menu). Mentre il menù è in scena l'ancora non disegna niente: al suo posto, allo stesso pixel, sta la superficie dell'host — la stessa capsula, che parte per il viaggio.
- `@Composable fun FluidMorphMenuHost(state: FluidMorphMenuState, backdrop: GlassBackdropState, modifier: Modifier = Modifier)` — L'host: da montare alla radice, sopra il contenuto e sotto niente. A riposo compone un nodo vuoto (niente gesti, niente disegno) che tiene le misure per l'apertura sincrona.

## `engine-ui/ui/fluidphysics/FluidPhysicsClip.kt`

- `fun Modifier.fluidPhysicsClip(state: FluidPhysicsState, active: () -> Boolean = { true }): Modifier` — Ritaglia il contenuto sulla silhouette VIVA di [state].

## `engine-ui/ui/fluidphysics/FluidPhysicsJourney.kt`

- `object FluidPhysicsJourney` — I viaggi di serie di [FluidPhysicsState.morphTo].
  - `val Spring: AnimationSpec<Float>` — La molla di serie: smorzamento 0,62 e risposta 0,36 s, i token del menu che si apre ([FluidMotion.MenuOpenDamping], [FluidMotion.MenuOpenStiffness]).
  - `val House: AnimationSpec<Float>` — La coreografia della casa (fino alla 2.13 il default): **parte piano, accelera, e si posa con un rimbalzo leggero di molla.** Una molla sola non sa partire piano (nasce alla massima accelerazione), quindi sono due tempi in una spec: una rincorsa in ease-in fino a [JourneyRunUpEnd] del viaggio, e da li' una molla sottosmorzata che eredita la velocita' con cui la rincorsa esce.

## `engine-ui/ui/fluidphysics/FluidPhysicsLiquid.kt`

- `val LocalFluidPhysicsLiquid: ProvidableCompositionLocal<Boolean>` — Se le superfici di Fluid-physics sotto questo punto sono di vetro liquido (3.0): la goccia e la lente in moto. Vero di serie.
- `data class FluidPhysicsDropTuning(val edgeTension: Float = FluidMotion.DropEdgeTensionDefault)` — Le manopole della goccia per la galleria (3.0). **Non e' API per le app**: esiste perche' Alessio possa tarare la goccia in mano sul telefono, e i numeri che sceglie diventano token di `FluidMotion`. Un'app che la usasse si legherebbe a un numero che domani cambia nome.
- `val LocalFluidPhysicsDropTuning: ProvidableCompositionLocal<FluidPhysicsDropTuning>` — Le manopole della goccia in vigore: vedi [FluidPhysicsDropTuning]. Solo per la galleria.

## `engine-ui/ui/fluidphysics/FluidPhysicsState.kt`

- `class FluidPhysicsState internal constructor(initial: FluidForm)` — Il cuore di Fluid-physics: possiede il viaggio fra due [FluidForm].
  - `val form: FluidForm get()` — La forma a cui la superficie è arrivata, o verso cui sta viaggiando.
  - `val progress: Float` — 0..1 lungo il viaggio corrente (con l'overshoot della molla), 1 a riposo. Da leggere in fase di disegno — dentro `graphicsLayer {}` o un draw block — mai in composizione: è lo stato che cambia a ogni fotogramma.
  - `fun driveExternally(from: FluidForm.Slab, to: FluidForm.Slab, progress: () -> Float, overshootInflationX: Float = 0f, overshootInflationY: Float = 0f)` — Il gancio per un componente che un orologio ce l'ha gia': la fisica diventa la *finestra* e il progresso resta quello delle molle del chiamante — due orologi sulla stessa superficie sono un disallineamento garantito. Pubblico dalla 1.13.0: e' cosi' che un'app aggancia il viaggio pill→player al proprio `Animatable`, drag compreso. Il drive resta installato finche' la superficie vive;
  - `fun driveExternally(from: FluidForm, to: FluidForm, progress: () -> Float)` — Come [driveExternally] fra due lastre, fra due forme di **sole lastre**: una lastra o un gruppo di lastre per parte (3.0, la barra ancorata delle pagine).
  - `val isMorphing: Boolean by derivedStateOf` — Grossolano e sicuro da leggere in composizione: dietro c'è un derivedStateOf.
  - `suspend fun morphTo(target: FluidForm, spec: AnimationSpec<Float>? = null, initialVelocity: Float? = null)` — Fa viaggiare la superficie verso [target] e sospende finché non si è posata.
  - `fun snapTo(target: FluidForm)` — Arriva senza viaggiare.
- `@Composable fun rememberFluidPhysicsState(initial: FluidForm): FluidPhysicsState` — Ricorda uno stato fisico. La forma iniziale conta solo alla prima composizione.

## `engine-ui/ui/fluidphysics/FluidPhysicsSurface.kt`

- `@Composable fun Modifier.fluidPhysicsSurface(state: FluidPhysicsState, backdrop: GlassBackdropState, tint: GlassTint, role: GlassRole = GlassRole.Floating, optics: GlassOptics = GlassDefaults.optics(role), tier: FluidPhysicsTier? = null, intensity: () -> Float = { 1f }, tintFrom: GlassTint? = null, tintBlend: () -> Float = { 1f }, opticsFrom: GlassOptics? = null, opticsBlend: () -> Float = { 1f }, sampleOnce: Boolean = false, exports: GlassBackdropState? = null, pressState: FluidPressState? = null): Modifier` — Un pannello di vetro la cui silhouette è quella di [state]: ferma quando lo stato riposa, viva mentre viaggia — con la rifrazione che segue la forma, non un'istantanea di dov'era.
- `enum class FluidPhysicsContentRole` — Da che lato del viaggio sta un contenuto.
- `fun Modifier.fluidPhysicsContent(state: FluidPhysicsState, contentRole: FluidPhysicsContentRole = FluidPhysicsContentRole.Incoming): Modifier` — Il contratto del contenuto durante un morph, impacchettato: il contenuto è misurato alla taglia del proprio estremo e non viene mai stirato — solo dissolvenza e uno zoom uniforme di qualche punto percentuale. È la lezione pagata dal design rigettato del 1.8.x ("una tendina tirata su un cartello già scritto"): la sagoma si trasforma, la scritta mai.

## `engine-ui/ui/fluidphysics/FluidPhysicsTier.kt`

- `enum class FluidPhysicsTier` — Quanto materiale un morph può portare su questo dispositivo, adesso.
