<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/fluid-physics.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Fluid-physics: il vetro che cambia forma

Il motore che trasforma **qualsiasi silhouette di vetro in qualsiasi altra**, con la rifrazione che segue la
forma mentre viaggia (`engine-ui`, package `ui.fluidphysics`). Esiste per rispondere a una regola vecchia
dell'engine — «niente shape morphing: un raggio animato ri-clippa a ogni fotogramma» — dandole una deroga sola,
con una disciplina propria: il clip del layer non insegue mai la sagoma, la silhouette la scolpisce lo shader, e a
riposo la superficie è un'istanza stabile. Il morphing è **transiente per contratto**.

**Modello mentale in due frasi.** Una superficie (`Modifier.fluidPhysicsSurface`) disegna il vetro nella
silhouette di uno stato (`FluidPhysicsState`); lo stato possiede un viaggio fra due forme (`FluidForm`: una
lastra, una sagoma libera, o un gruppo di lastre che si fondono) che si muove con una molla. Il **contenuto** non
si trasforma mai con la sagoma: si dissolve e fa uno zoom uniforme, la sagoma si stira, la scritta no.

Come si lega alle altre famiglie. Il materiale (ottica, tinta, ruolo, fondale `GlassBackdropState`, la «regola
della taglia») è della famiglia **vetro**: [vetro.md](vetro.md) — qui si usa, non si ridefinisce. I menu che si aprono col
dito (pannello, relè del puntatore, voci, aptica, popover, finestra che si espande) sono della famiglia
**menu-modali**: [menu-modali.md](menu-modali.md) — qui c'è solo il loro motore e il tasto `FluidMorphMenuButton`. Dentro il
motore (piano di resa, goccia, shader, bordo, ombra) e come si modifica: [fluid-physics-motore.md](fluid-physics-motore.md).
Tutte le firme: [../api/fluid-physics.md](../api/fluid-physics.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la card/riga che diventa pop-up, il menu contestuale di una riga, le azioni della barra | **niente**: i preset usano già il motore (dalla 1.9.7; dalla 3.0 anche la goccia). Vedi «Cosa arriva gratis» |
| un tasto che si apre nel proprio menu | `FluidMorphMenuButton` + un `FluidMorphMenuHost` alla radice (sezione «Il tasto che si apre nel proprio menu») |
| una forma mia (cerchio, stella, scarabocchio) che diventa un'altra | `rememberFluidPhysicsState` + `Modifier.fluidPhysicsSurface` + `state.morphTo(forma)` |
| una forma già impaginata che deve solo *essere* vetro, ferma | **non** questo: `glassSurface` ([vetro.md](vetro.md)). A riposo le due cose sono la stessa lastra, ma la superficie fisica porta lo stato e il costo del viaggio |
| due tasti che si fondono in un pannello | `FluidForm.Group` (fino a 6 lastre, ponte di materiale fra pezzi vicini) |
| una forma disegnata col dito | `FluidFormPresets.fromFreehand(punti)` |
| un viaggio guidato dal **mio** orologio (un `Animatable`, un drag) | `state.driveExternally(from, to, progress)`: la fisica diventa la finestra, il progresso resta tuo |
| un testo/icone dentro la superficie che si trasforma | `Modifier.fluidPhysicsContent(state, ruolo)` |
| un'interfaccia intera (pagina a schermo pieno) che entra nella finestra | `Modifier.fluidPhysicsClip(state)` sul contenuto, mai sulla superficie |
| un rimbalzo | la molla di serie lo dà già, come **gonfiore** della sagoma. Mai `graphicsLayer { scaleX }` su un vetro (prima trappola) |
| un raggio che cambia in una schermata | non animarlo a mano: una forma che si trasforma chiede un `FluidPhysicsState` |
| un morph demo da tocco secco, con partenza lenta | `morphTo(forma, FluidPhysicsJourney.House)` |
| spendere meno su un telefono vecchio | niente: il tier scende da solo. Per forzare più povero: `fluidPhysicsSurface(tier = FluidPhysicsTier.Balanced)` |

## Cosa arriva gratis aggiornando l'engine

Dalla **1.9.7** i preset che già si usavano sono passati al motore, quindi ogni app che aggiorna li trova
trasformati **senza cambiare una riga**:

- **La card che si espande** (`FluidGlassModalPortal` con `origin`, presentazione `Expand`): la finestra è una
  superficie Fluid-physics che viaggia dal rettangolo dell'ancora al pannello con la rifrazione addosso.
  `Modifier.fluidExpandOrigin` fa il resto da solo: registra l'immagine della riga e la nasconde mentre la finestra
  è in scena, così la riga *diventa* il pop-up invece di restarci sotto. Il viaggio è guidato dal suo orologio
  (`driveExternally`: le molle del modale legate a dissolvenze, scrim e ritirata del back predittivo).
- **Il tasto che si apre nel proprio menu**: `FluidMorphMenuButton` (questo si chiama: è il sostituto di
  `FluidGlassMenuButton`, deprecato).
- **Dalla 3.0**: il menu contestuale delle righe, le azioni della barra (`FluidBarAction(actions)`), il popover,
  l'avviso `FluidAlert` in host e la **barra ancorata delle pagine** (`FluidPageBar`, che guida un gruppo di lastre col
  progresso del dito) passano dalla stessa finestra. Ogni pannello di vetro dell'host è una superficie Fluid-physics.
- **Restano com'erano, per scelta**: `FluidGlassModalPresentation.Sheet` (il foglio) e il pannello classico, che
  sopravvive solo come ripiego opaco quando non c'è un fondale.

Quando serve chiamare qualcosa: per il tasto-menu (`FluidMorphMenuButton` + host), per una forma inventata
(`rememberFluidPhysicsState` + `fluidPhysicsSurface`), per agganciare un viaggio al proprio `Animatable`
(`driveExternally`).

## Il modello: forme, stato, viaggio

```
FluidForm (cosa) ──morphTo / driveExternally──► FluidPhysicsState (quando) ──ensurePlan──► fluidPhysicsSurface (come si vede)
   Slab | Poly | Group                           form · progress · isMorphing              lente, tinta, bordo, ombra
                                                                                           fluidPhysicsContent / fluidPhysicsClip (il contenuto)
```

- **Le coordinate sono pixel nello spazio del nodo che ospita la superficie** (il suo angolo in alto a sinistra è
  0,0), non dp: una forma è geometria di fase di disegno. Chi costruisce una forma converte una volta con la
  densità che ha in mano. Il nodo può essere **più grande della sagoma** (il palco del Playground, lo schermo
  intero del modale): tutta la geometria è nella forma, mai nella taglia del nodo.
- Un viaggio è «una forma sorgente, una destinazione, una molla». La forma da cui parte un viaggio è sempre dove la
  superficie **è adesso**, anche a metà di un altro viaggio (ritargeting).

## FluidForm e i preset

**Cos'è.** Il vocabolario del sottosistema: qualsiasi cosa il motore trasformi prima diventa uno di tre casi.
`FluidForm.Slab` è la famiglia di lavoro (un rettangolo a quattro raggi: rettangolo, quadrato, cerchio, capsula,
angolo continuo; interpolarne due è un lerp di otto numeri e la rifrazione resta piena per tutto il viaggio);
`FluidForm.Poly` è una sagoma chiusa qualsiasi; `FluidForm.Group` sono fino a otto lastre rese come **una** superficie
liquida (sei fino alla 3.x).

```kotlin
FluidForm.Slab(frame: Rect, cornerRadii: FluidCornerRadii = FluidCornerRadii.Zero,
               smoothing: Float = ContinuousCornerShape.IosSmoothing)
FluidForm.Poly(frame: Rect, vertices: List<Offset>, rounding: Float = 0f)   // vertici 0..1 nel frame, in senso orario di schermo
FluidForm.Group(pieces: List<FluidForm>, blendRadius: Float = 48f)           // pezzi: solo Slab, da 1 a FluidForm.Group.MaxPieces (8)
FluidForm.circle(center, radius)                                            // la forma di riposo del Playground
FluidForm.slabOf(shape: CornerBasedShape, frame, density, layoutDirection)  // un componente entra col suo FluidRadius
```

**Uso.** I preset (`FluidFormPresets`) sono costruibili su qualsiasi frame, perché un morph parte sempre da dove la
forma è: un preset che imponesse la propria posizione farebbe saltare il pezzo prima di trasformarlo.

```kotlin
FluidFormPresets.square(frame, radius = cardRadiusPx)     // centrato, lato = lato corto
FluidFormPresets.rectangle(frame, radius = 0f)
FluidFormPresets.capsule(frame)                           // raggio = metà del lato corto, mai un numero fisso
FluidFormPresets.trapezoid(frame, topInsetFraction = 0.22f, rounding = 12f)
FluidFormPresets.star(frame, points = 5, innerRatio = 0.45f, rounding = 8f)
FluidFormPresets.blob(frame, seed = 3)                    // deterministica per seed: stessa macchia a ogni ricomposizione
FluidFormPresets.fromFreehand(points, rounding = 6f)      // tratto a mano libera → Poly trasformabile
```

**Comportamento.**
- `Slab`: i raggi sono **pixel assoluti** (non Dp) e a disegno si tengono fra 0 e mezzo lato corto. Il raccordo continuo
  (`smoothing`) è onorato **a riposo**; in viaggio la sagoma passa per angoli circolari (la differenza sta dentro il
  pixel finché qualcosa si muove). `FluidForm.slabOf` legge i quattro angoli di una `CornerBasedShape` rispettando
  la direzione del layout; `smoothing` vale `IosSmoothing` solo se la forma è una `ContinuousCornerShape`, 0 altrimenti.
- `Poly`: `require` fra 3 e `FluidFormPresets.MaxVertices` (64) vertici, altrimenti `IllegalArgumentException`. I vertici sono
  di *controllo* (non il campionamento di resa); il raccordo `rounding` è uniforme, in pixel; per più fedeltà a un
  vertice si aggiungono vertici.
- `Group`: `require` non vuoto, al massimo 8 pezzi (`FluidForm.Group.MaxPieces`; 6 fino alla 3.x), **solo `Slab`**, niente
  gruppi dentro gruppi (è una disposizione, non un albero). Il costo dello shader di gruppo è `pezzi × pixel`: otto
  rettangoli coprono ogni barra e ogni gruppo di tasti che l'engine disegna. Il `frame` di un gruppo è l'unione dei pezzi. `blendRadius` è il raggio di fusione in
  pixel; il ponte esiste **solo al tier `Full`** (sotto, i pezzi restano geometria separata che si sovrappone: più
  povero, mai rotto). Col materiale nuovo (`Liquid`: «Comportamento» di `fluidPhysicsSurface`, sotto) il ponte esiste **solo in viaggio**:
  a riposo i pezzi di un gruppo sono staccati del tutto, anche quando si toccano, e il collo nasce e si scioglie dentro
  il viaggio (`GlassLiquidTravel.neck`): nasce nella prima metà e si spegne nell'ultimo 32% (`GlassLiquidNeck`), senza
  cuspidi a V. Un viaggio interrotto riparte dal collo che c'era. Aperto: vita e ponte durano circa 33 ms, contro gli
  oltre 150 della 3.1.2, perché il collo segue il progresso e non il tempo che manca alla posa. Col vetro di prima a `Full`
  due pezzi vicini restano saldati anche fermi. Il raggio effettivo si spegne man mano che i pezzi si compenetrano, perché `smin(a, a, k) = a − k/4`
  gonfierebbe l'unione di un quarto del raggio e alla posa l'alone sparirebbe di colpo.
- `blob` ha 12 vertici, `irregularity` 0..0,6; `star` ha `points` fra 3 e 32; `trapezoid` tiene l'inset fra 0 e 0,45.
- `fromFreehand`: semplifica (Ramer–Douglas–Peucker, tolleranza 1,5% della diagonale), toglie il punto doppio se il dito
  è tornato entro il 5% della diagonale dalla partenza, chiude, ricampiona a `FluidFormPresets.FreehandVertices` (48)
  passi uguali di perimetro e normalizza il verso. **Sotto tre punti distinti, o con diagonale sotto 24 px, degrada a un
  cerchio** di raggio almeno 48: un tocco non diventa una forma vuota.

**Trappole.**
- Il KDoc di `FluidForm.Group` dice «pezzi `Slab` o `Poly`»; il codice richiede **solo `Slab`** (e il messaggio lo dice).
  Una sagoma libera viaggia da sola; per i viaggi gruppo↔sagoma c'è lo scalo automatico (vedi `morphTo`).
- `FluidFormPresets.fromFreehand` ha come `frame` il riquadro del tratto, non quello del palco: se la forma deve stare
  in un punto preciso, costruirla dopo aver scelto il frame o traslarla.

**Versione.** `FluidForm`/preset dalla 1.9.0.

## FluidPhysicsState

**Cos'è.** Il cuore: possiede il viaggio fra due `FluidForm`. Il contratto che esiste per onorare è: **un fotogramma
di morph = nuovi uniform su uno shader già compilato, più un passaggio GPU** — mai una ri-cattura del fondale, un re-clip
del layer, un cambio di padding. A progress 0 e 1 la superficie tiene un'istanza stabile, quindi una pagina che
scorre con un pezzo fermo sopra non paga niente del morph.

**Firma essenziale.**

```kotlin
@Composable fun rememberFluidPhysicsState(initial: FluidForm): FluidPhysicsState   // initial conta solo alla prima composizione

class FluidPhysicsState {
  val form: FluidForm                 // la forma a cui è arrivata, o verso cui viaggia
  val progress: Float                 // 0..1 lungo il viaggio (con overshoot), 1 a riposo — SOLO in fase di disegno
  val isMorphing: Boolean             // derivedStateOf: sicuro in composizione
  suspend fun morphTo(target: FluidForm, spec: AnimationSpec<Float>? = null, initialVelocity: Float? = null)
  fun snapTo(target: FluidForm)
  fun driveExternally(from: FluidForm.Slab, to: FluidForm.Slab, progress: () -> Float,
                      overshootInflationX: Float = 0f, overshootInflationY: Float = 0f)
  fun driveExternally(from: FluidForm, to: FluidForm, progress: () -> Float)   // gruppi di sole lastre
}
```

**Uso** (dal Playground/`PracticalDemos` del sample):

```kotlin
val state = rememberFluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))   // segnaposto: lo stage non è ancora misurato
var stage by remember { mutableStateOf(IntSize.Zero) }
val scope = rememberCoroutineScope()
LaunchedEffect(stage) { if (stage != IntSize.Zero) state.snapTo(formFor(phase)) }   // la taglia nuova arriva SENZA viaggiare
// …al tocco:
scope.launch { state.morphTo(formFor(target)) }
```

**Comportamento.**
- `morphTo` sospende finché la molla non si è posata. Con `spec` nullo il viaggio è **una molla sola**,
  `FluidPhysicsJourney.Spring` (3.0), che parte «alla velocità del momento»: zero da fermo, quella del viaggio
  interrotto in un ritargeting, quella del gesto se si passa `initialVelocity` (in *viaggi al secondo*). Fino alla 2.13
  il default era la coreografia della casa; oggi è il preset esplicito `FluidPhysicsJourney.House`.
- **Ritargeting a metà volo legittimo**: il viaggio nuovo parte dalla geometria di *adesso*, ereditando la velocità
  proiettata sulla strada nuova, tenuta fra −3 e +6 viaggi/s (un ritargeting non riparte mai da fermo; un viaggio
  nuovo più corto di un pixel non eredita niente).
- **Non esistono coppie vietate.** L'unico viaggio che il renderer non sa fare in un colpo — gruppo↔sagoma libera — diventa da
  sé in **due tappe** passando per uno scalo: una goccia (raggio pieno) sul footprint della *destinazione*, la prima tappa
  criticamente smorzata e rapida (`FluidMotion.snappy()`), così si legge come un viaggio solo. Gli altri accoppiamenti
  gruppo↔gruppo accoppiano i pezzi al costo minimo (distanza dei centri + differenza di taglia, parità risolta per indice:
  deterministico): in una fusione 2→1 entrambe le sorgenti puntano alla stessa destinazione, in una scissione 1→2 due
  copie della sorgente partono insieme.
- **Un viaggio cancellato si ferma dove si trova** (3.0, `FluidPhysicsCancelTest`): la coroutine cancellata lascia la
  forma interpolata di quell'istante, e il `morphTo` successivo parte da lì. Prima saltava al traguardo per un
  fotogramma (il menu chiuso mentre si apriva compariva come pannello pieno).
- **Animazioni ridotte** (`LocalFluidMotionPolicy`, scala durata 0): `morphTo` arriva senza viaggiare e la goccia non c'è.
  Lo stato lo legge dalla composizione di `rememberFluidPhysicsState` e di `fluidPhysicsSurface`.
- `morphTo` verso la forma in cui si è già (a riposo) non fa niente.
- `snapTo` arriva senza viaggiare e annulla un viaggio in corso.

**Trappole.**
- `progress` e il contenuto di `form` cambiano a ogni fotogramma: **si leggono in fase di disegno** (`graphicsLayer {}`,
  un draw block), mai in composizione. Per la composizione c'è `isMorphing`.
- **`isMorphing` è falso con `driveExternally`**: riflette solo i viaggi di `morphTo`.
- **Dopo `driveExternally` il drive resta installato finché la superficie vive** (non esiste un modo di toglierlo):
  da lì `morphTo`/`snapTo` non si vedono più, perché `progress` e il piano prendono il drive esterno. Uno stato è o
  «`morphTo`» o «guidato da fuori», non entrambi. E `state.form` resta quello iniziale: `fluidPhysicsContent` (che
  legge `form`) ha senso solo con `morphTo`/`snapTo`.
- La coroutine di `morphTo` è del chiamante: se la composizione se ne va (scope cancellato) il viaggio si ferma
  *dove si trova* — è voluto — e il successivo riparte da lì.
- `rememberFluidPhysicsState(initial)`: la forma iniziale conta solo la prima volta. Se lo stage non è ancora misurato,
  mettere un segnaposto e fare `snapTo` quando arriva la taglia, come fa il sample.

**Versione.** `morphTo`/`snapTo`/`rememberFluidPhysicsState` dalla 1.9.0; `initialVelocity`, la molla sola di serie, la
velocità ereditata, l'oltrepasso come gonfiore: 3.0.0.

## driveExternally: il viaggio al ritmo del tuo orologio

**Cos'è.** Per i componenti che un orologio ce l'hanno già (il modale con le sue molle, la pillola che diventa
player, la barra delle pagine che segue il dito): la fisica diventa la **finestra** e il progresso resta quello del
chiamante, perché due orologi sulla stessa superficie sono un disallineamento garantito. Pubblico dalla 1.13.0 per le app
(pillola→player agganciata al proprio `Animatable`, drag compreso).

```kotlin
// fra due lastre, con il gonfiore dell'oltrepasso per asse
state.driveExternally(from = slabA, to = slabB, progress = { anim.value },
                      overshootInflationX = 0.04f, overshootInflationY = 0.06f)
// fra due gruppi di lastre (3.0: la barra ancorata delle pagine)
state.driveExternally(from = groupA, to = groupB, progress = { fraction })
```

**Comportamento.**
- La sagoma è il lerp `from`→`to` a `progress()`. **Oltre l'1** (la molla ha overshoot) la sagoma non prosegue lungo il
  viaggio: è il traguardo **gonfiato attorno al proprio centro** di `1 + (progress − 1) · overshootInflation` per asse.
  I due assi sono separati perché hanno vincoli diversi (lo schermo limita i fianchi, non il fondo: si può smorzare la X
  senza togliere vita alla Y). A zero (default) oltre l'1 resta il traguardo fermo; **fra gruppi non c'è mai oltrepasso**.
- **Richiamarlo con gli stessi estremi è gratis**, ma **la lambda `progress` resta quella della prima chiamata**: la seconda
  chiamata a estremi uguali ritorna subito senza sostituirla. Deve quindi leggere sempre dallo stesso oggetto di stato.
  Con estremi diversi (anche solo un rettangolo) il drive si reinstalla.
- Fra gruppi: solo lastre (un `Poly` fa `IllegalArgumentException`). Un pezzo senza compagno dall'altra parte lo riceve il
  chiamante come **seme**, una lastra di un pixel al centro del pezzo, così nasce (o si spegne) sul posto invece di
  attraversare la barra.
- Con un solo pezzo per parte la superficie è anche la **goccia** (vedi «A riposo una lastra, in viaggio una goccia»).
- Esiste una variante interna con `startFrame` (3.0, per i menu): la partenza viva è il rettangolo di *questo istante*,
  perché un tasto premuto è più grande di quello a riposo e la pressione si scioglie mentre il viaggio già corre. Non è
  API pubblica.

**Trappole.** Vedi `isMorphing` e «drive che resta» sopra. In `fluidPhysicsSurface` l'`intensity`, non un alpha esterno,
deve fare le dissolvenze.

## Modifier.fluidPhysicsSurface

**Cos'è.** Un pannello di vetro la cui silhouette è quella dello stato: ferma quando riposa, viva mentre viaggia. È
costruita su `drawBackdrop`, accanto a `glassSurface` e con la sua stessa grammatica (ruoli, ottiche, tinte, qualità).

```kotlin
@Composable
fun Modifier.fluidPhysicsSurface(
  state: FluidPhysicsState, backdrop: GlassBackdropState, tint: GlassTint,
  role: GlassRole = GlassRole.Floating, optics: GlassOptics = GlassDefaults.optics(role),
  tier: FluidPhysicsTier? = null, intensity: () -> Float = { 1f },
  tintFrom: GlassTint? = null, tintBlend: () -> Float = { 1f },       // la pellicola scivola da tintFrom a tint
  opticsFrom: GlassOptics? = null, opticsBlend: () -> Float = { 1f }, // 3.0: il materiale viaggia (blur, lente, cupola, frangia, bordo, ombra)
  sampleOnce: Boolean = false, exports: GlassBackdropState? = null,
  pressState: FluidPressState? = null,                                // 3.0: lente più profonda e bagliore sotto il dito
): Modifier
```

**Uso** (`PlaygroundStage.kt`):

```kotlin
Box(
  Modifier.fillMaxSize().fluidPhysicsSurface(
    state = state, backdrop = stageBackdrop,
    tint = GlassDefaults.floatingTint(), role = GlassRole.Floating,
    tier = tier,
  ),
)
```

**Comportamento.**
- **Due materiali, come `glassSurface`** (4.0). Al tier `Full`, da Android 13, dove `LocalFluidGlassModel` non chiede
  `Classic` e il compilatore accetta le due sorgenti del materiale nuovo che servono qui (`GlassBody8ShaderString` e
  `GlassBodyRingShaderString`), la superficie è disegnata dal **materiale `Liquid`**, lo stesso corpo di `glassSurface`:
  stesso velo, smerigliatura, filo, riflesso e ombra, **fermi e in viaggio**. Altrimenti (`Balanced`, `Lite`, `Classic`
  chiesto, una sorgente rifiutata) è il vetro di prima, al pixel com'era. Decide `resolvePhysicsGlass`, pura (la tabella
  di verità sta in `PhysicsLiquidTest` e `GlassRecipeParityTest`); un rifiuto fa tornare la superficie al vetro di prima
  **per intero**, mai una goccia nuova che si posa su una lastra di prima.
- **Col materiale nuovo, a riposo è la `glassSurface` della stessa forma** (stesso ruolo, ottica, taglia, tema, intensità,
  qualità e tinta: stessi numeri di corpo, luce e ombra, provato da `GlassRecipeParityTest`), e il primo e l'ultimo
  fotogramma di un viaggio sono la superficie ferma: niente scalino alla partenza né all'arrivo, perché la ricetta di
  viaggio (`glassTravelRecipe`) a moto zero non cambia un bit. Luce e ombra stanno **nel corpo** anche da ferma, quindi
  fermo e primo fotogramma del viaggio sono lo stesso programma con gli stessi uniform; il ritaglio del nodo che una
  `glassSurface` ha dal suo nodo qui lo emula il corpo. **In viaggio** il vetro è un po' più limpido e la lente un po'
  più forte, quanto la sagoma si muove e mai da ferma (`GlassLiquidProfile.travel`); la tinta e l'ottica di partenza e
  d'arrivo (`tintFrom`, `opticsFrom`) si mescolano campo per campo, esatte ai capi. Anche il velo dei grandi sul tema
  scuro, che fra un piccolo e un grande va da quello della partenza a quello dell'arrivo in 0,3 s, al primo fotogramma
  del viaggio è ancora quello della superficie ferma: il suo tempo parte quando cambia la meta. Richiudendo, il velo
  torna a quello del tasto seguendo il verso del gesto (la risalita della molla di chiusura non è una riapertura), e
  con `snapTo` o col movimento ridotto si posa subito.
- **Col vetro di prima** a riposo è una lastra della 3.0: la stessa funzione di `glassSurface` di prima per sfocatura,
  lente, frangia e pressione (`physicsLensUniforms`), il bordo e l'ombra registrati con la ricetta del renderer. Che la
  taglia che conta sia quella della **sagoma** (non del nodo) vale per i due materiali: è la «regola della taglia» di
  [vetro.md](vetro.md).
- Cosa la separa da `glassSurface`: il clip del layer **non insegue la sagoma** (al tier `Full` resta il rettangolo del
  nodo e la silhouette la scolpisce lo shader); il nodo può essere più grande della sagoma; il margine della cattura è
  **uno per tutto il viaggio** (col materiale nuovo `glassTravelPaddingDp`: la sfocatura piena del ruolo alla crescita
  massima dei grandi), e col materiale nuovo la cattura è a risoluzione piena.
- **L'ombra.** Col materiale nuovo è nel corpo, la stessa delle maschere a riposo di una lastra di quella taglia, una
  sola anche sui ponti; si vede **solo dentro il nodo**, perché il layer del vetro è ritagliato lì: un palco grande
  quanto la finestra (menu, modale, Playground) la contiene, un nodo stretto attorno alla sagoma va allargato
  (`glassTravelShadowReachDp`, come fa la barra fra le pagine), o l'ombra esce tagliata a filo del nodo. Col vetro di
  prima l'ombra del ruolo è disegnata **fuori dal layer del vetro** (prima di `drawBackdrop`); se qualcuno ne disegna
  già una per lo stesso stato da fuori (`fluidPhysicsShadow`, la finestra dei modali) la sua tace — due ombre sono il
  doppio del peso di una lastra.
- `tintFrom`/`tintBlend` e `opticsFrom`/`opticsBlend` sono lambda lette **in fase di disegno**; il blend è tenuto fra 0 e 1
  (la molla ha overshoot ma il materiale non oltrepassa mai i propri capi: un terzo materiale non l'ha chiesto nessuno).
  Il patto del padding regge per costruzione fra due ottiche di serie (la sfocatura non supera mai l'altezza della lente
  che la copre, quindi il margine di cattura vale zero e non cambia per tutto il viaggio); chi passa ottiche scritte a
  mano deve tenerle così ai due capi.
- `intensity` (0..1): sotto 0,001 non si disegna e non si registra niente. È **il modo** di far sfumare la superficie.
- `exports`: come in `glassSurface`, la superficie registra anche *se stessa* (materiale, tinta, bordo) perché un'altra
  superficie la possa rifrangere (i tasti tondi dentro una capsula che si trasforma); si abbina a
  `rememberCombinedGlassBackdrop`. Al tier `Lite` non registra niente. La registrazione segue `intensity`; un alpha messo da
  fuori sul nodo no (terza trappola).
- `pressState`: pensato con `fluidPressable(pressState = )` posato sulla sagoma; il dito si legge nelle coordinate
  dell'elemento premuto, assunto posato sul riquadro della sagoma. **Niente crescita**: scalare il layer di un vetro è la
  prima trappola, e una superficie che si trasforma cede, non si gonfia sotto il dito. Con animazioni ridotte il bagliore vale 0,4.
- **Tier**: `null` = il tetto del dispositivo; una richiesta può solo *scendere* (vedi «Costi e tier»).

**Trappole.**
- La superficie legge `LocalFluidMotionPolicy` e `LocalFluidPhysicsLiquid` per decidere se c'è la goccia.
- Bordo: col materiale nuovo è il filo e il riflesso del profilo, nel corpo, sulla sagoma di ogni fotogramma (ponti
  compresi). Col vetro di prima sul lato chiaro è una riga **scura** in `SrcOver`, sullo scuro luce in `Plus`
  (`glassRimRecipe`); non si gira il solo colore.
- Col materiale nuovo l'ombra si vede solo dentro il nodo: un nodo stretto attorno alla sagoma la taglia (sopra,
  «L'ombra»). Si verifica a schermo: un'ombra che finisce con uno spigolo dritto a pochi dp dalla sagoma.
- Un `Modifier.shadow` di piattaforma sotto un vetro trapela dentro la sagoma: l'ombra è del vetro.

**Non usarla quando.** Il pannello sta fermo e nessuno lo trasforma: `glassSurface`.

**Versione.** 1.9.0; `exports` 2.9.0; `opticsFrom`/`opticsBlend`/`pressState`, «a riposo una lastra», bordo e ombra del vetro: 3.0.0;
il materiale `Liquid` fermo e in viaggio, gruppi a otto pezzi, collo solo in viaggio, ombra nel corpo: 4.0.0.

## Contenuto: fluidPhysicsContent e fluidPhysicsClip

**Cos'è.** Il contratto 1.8.x «la sagoma si trasforma, la scritta mai» (la lezione del design rigettato, «una tendina
tirata su un cartello già scritto»), impacchettato.

```kotlin
Modifier.fluidPhysicsContent(state, contentRole = FluidPhysicsContentRole.Incoming)   // Outgoing | Incoming
Modifier.fluidPhysicsClip(state, active = { true })                                    // ritaglia sulla silhouette VIVA
```

**Comportamento.**
- `fluidPhysicsContent` è un `graphicsLayer` letto in disegno su `state.progress` (tenuto 0..1):
  - **Outgoing** (il contenuto della forma che si lascia): alpha `1 − progress/0,35`, scala `1 − 0,06 · progress`. Se ne va nella prima parte.
  - **Incoming** (il contenuto della forma d'arrivo): alpha `(progress − 0,25)/0,5`, arriva nella seconda parte, così
    i due testi non sono mai leggibili insieme. **Viaggia con la superficie**: è posato sul frame d'arrivo e finché il
    pannello è altrove lo raggiunge, traslato sul centro corrente della sagoma e scalato su `min(lato corto corrente /
    lato corto d'arrivo)` tenuto fra **0,72 e 1,12**. Con un **gruppo** in arrivo la traslazione si spegne (due etichette
    trascinate verso il centro dell'unione andrebbero nel posto sbagliato) e la scala è `0,92 + 0,08 · progress`.
  - Il contenuto sta posato sul frame della sua fase e **si misura alla propria taglia**: il modifier non misura niente,
    non stira niente — la posa è del chiamante (`FrameBox` nel sample).
  - La posizione segue il corpo senza il tremore della gelatina (`contentBoundsNow`): il testo non trema.
- `fluidPhysicsClip` serve quando il traguardo è la **finestra intera**: un contenuto impaginato a schermo pieno e posato
  lì esce dalla sagoma per tutta la prima metà del viaggio, e al tier `Full` il clip hardware della superficie resta il
  rettangolo del nodo. Si usa **sul contenuto, mai sulla superficie**. Non è `@Composable` e legge in disegno. Per un morph
  fra una lastra e una lastra il clip è un `Outline.Rounded` (clip hardware, economico); per gruppo o sagoma libera è un
  path (maschera il nodo fuori schermo a ogni fotogramma: l'unica cosa onesta che quella geometria consente).
- `active`: a riposo un clip su un nodo a schermo intero è lavoro per niente; tenerlo acceso per tutto ciò che non è riposo, spento ai due capi.

**Trappole.**
- Il KDoc di `fluidPhysicsContent` parla di contenuto «misurato alla taglia del proprio estremo» e di «qualche punto
  percentuale» di zoom: il modifier è solo il `graphicsLayer`, e la scala dell'Incoming arriva a ±28%/+12% seguendo la
  sagoma. La misura giusta è responsabilità di chi posa il contenuto.
- I due ruoli vanno **scambiati dal chiamante** quando cambia la fase: a riposo `progress = 1`, e un contenuto `Outgoing` è
  invisibile (alpha 0). Il sample assegna `Incoming` allo strato della fase corrente e `Outgoing` agli altri.
- Il modale e il menu non montano il modifier: applicano **la stessa regola** con una mappa propria
  (`fluidMenuContentMap`, `fluidMenuLiquidContentMap`, le finestre `LiquidContent*`), che segue il baricentro della goccia e ritaglia dentro il vetro di adesso.

**Versione.** `fluidPhysicsClip` 1.17.0; `fluidPhysicsContent` dalla 1.9.0 (il contenuto che viaggia con la sagoma dalla 1.9.2).

## A riposo una lastra, in viaggio una goccia

**A riposo** una superficie fisica è una lastra: stesso materiale di `glassSurface` (col materiale nuovo, gli stessi
numeri di corpo, luce e ombra; col vetro di prima, la stessa lente con bordo e ombra del renderer), e *nessun
fotogramma chiesto* (il conducente della goccia aspetta con uno `snapshotFlow` finché niente si muove).

**In viaggio** una superficie che va fra due **lastre** (un solo pezzo per parte: `morphTo` e `driveExternally`
a una lastra) è una **goccia** (3.0, variante C del banco scelta da Alessio: «ciò che rende davvero liquido un materiale è
che non è geometricamente ben definito»): il contorno è fatto di 48 punti presi dalla sagoma a riposo di quell'istante, ognuno su una molla
verso il suo punto — dura accanto all'ancora (il tasto, la riga, il centro della partenza), morbida lontano — con una
tensione del bordo leggera fra i vicini. Lontano dall'ancora i punti restano indietro: la coda attaccata al tasto fa da collo.
In moto il vetro si fa più limpido e piega di più, poi alla posa torna esattamente la lastra. Col materiale nuovo è la
ricetta di viaggio del profilo (`GlassLiquidProfile.travel`: più nitido nella mescola, lente più forte, cupola, un
filo d'ingrandimento e di frangia; il raggio di sfocatura e la banda della lente no, perché decidono il margine della
cattura), e mentre la goccia si calma il suo campo passa a quello della lastra che la sostituirà, così alla posa non
resta niente da cambiare. Col vetro di prima è la lente in moto della 3.0 (altezza +60%, spostamento +80%, sfocatura
−45%, ingrandimento al centro 3,5%, un filo di frangia).

- **Primo fotogramma = lastra del mittente**; sotto ¼ di pixel dal riposo, e sotto 3 dp/s, la goccia si posa e il piano torna
  quello di prima *al bit*, senza gradino.
- **Restano geometrici** (lerp e, per i gruppi, ponte): i **gruppi**, le **sagome libere** (`Poly`) e `FluidSearchField`. Anche
  senza goccia, il rimbalzo è sempre un gonfiore.
- La goccia scorre al **tempo delle animazioni** (diviso per la scala durata del sistema): a rallentatore la coda resta
  indietro quanto a velocità piena; a scala zero non c'è.
- `LocalFluidPhysicsLiquid` (vero di serie) la spegne e `LocalFluidPhysicsDropTuning` (`FluidPhysicsDropTuning(edgeTension)`,
  di serie 0,2) la tara: **sono manopole di debug della galleria, non API per le app** (i numeri scelti diventano token
  `FluidMotion.Drop*`; un'app che le usasse si legherebbe a un numero che domani cambia nome).
- Dettagli (molle, soglie, come si disegna) in [fluid-physics-motore.md](fluid-physics-motore.md).

## La molla, e quando i contenitori rimbalzano

È la questione più discussa del motore, e il codice ha cambiato idea due volte.

1. **1.9.0**: «la molla dei contenitori è `standard`, mai `fluid`: nei fotogrammi delle transizioni di iOS i pannelli non
   rimbalzano, il rimbalzo è degli elementi piccoli; e una fusione non estrapola mai oltre il coincidere».
2. **Corretta più tardi (la «Fase 11» di `ADOZIONE.md`)**: **la card ancorata rimbalza**, per richiesta esplicita («eccessivamente in fuori su
   tutti i lati, poi torna»). Il rimbalzo è però *geometrico*: un gonfiore uniforme della sagoma che si posa, con un tetto
   sull'asse X perché al picco la card non finisca a ridosso dei bordi.
3. **3.0**: l'oltrepasso è sempre un **gonfiore uniforme attorno al centro del traguardo**, mai il lerp estrapolato oltre l'1
   (che per una riga larga che diventa una card stretta voleva dire fianchi in dentro e fondo in fuori: una sagoma
   stirata). Guadagno `0,6` (lo stesso del menu), massimo `8%` della taglia, e al più **12 dp per lato** (meno del margine di
   16 dp che un pannello a tutta larghezza tiene dallo schermo, così al picco non lo tocca mai). Con la molla di serie il picco
   d'oltrepasso è 8,4% e il gonfiore `0,6 × 8,4% ≈ 5%`: il tetto non scatta, trattiene una molla più viva o una velocità ereditata alta.
   La scala è **una**, uniforme: il lato lungo decide il tetto in pixel, il corto lo segue (un pannello che al picco
   cambiasse proporzioni sarebbe di nuovo uno stiramento).

Cosa **non** rimbalza, comunque: lo scalo dei viaggi gruppo↔sagoma (criticamente smorzato, `FluidMotion.snappy()`), la
**fusione** (all'oltrepasso tutti i pezzi diventano il traguardo gonfiato, che coincidono: un contorno solo, ponte spento), il
back predittivo delle pagine, e le superfici ferme. **Il ritorno di un menu invece oltrepassa**: `FluidMotion.dropClose` ha
smorzamento 0,72 (non critico) e sotto lo 0 la partenza si sgonfia attorno al proprio centro (fino a metà della taglia), il ritorno che
oltrepassa l'ancora e si ricompone. Sotto lo 0, in generale, la sagoma prosegue la strada che aveva: è il moto di prima che si esaurisce.
(`FluidMotion.menuClose`, critico e senza rimbalzo, esiste fra i token ma il menu della 3.0 non lo usa.)

**La coreografia della casa** (`FluidPhysicsJourney.House`, il default fino alla 2.13): parte piano, accelera, si posa con un rimbalzo leggero. Una molla
sola non sa partire piano, quindi sono due tempi in una spec: una rincorsa in ease-in (curva `(0,55; 0; 0,72; 0,4)`, 180 ms) fino
all'80% del viaggio, poi una molla sottosmorzata (0,58; risposta 0,20 s) che eredita la velocità con cui la rincorsa esce.
La velocità iniziale passata a `morphTo` **non conta** con questo preset: la partenza lenta è il punto. È giusta per un
morph dimostrativo avviato da un tocco secco (il Playground), **sbagliata per tutto ciò che l'utente sta aspettando**.

## FluidPhysicsJourney

```kotlin
object FluidPhysicsJourney {
  val Spring: AnimationSpec<Float>   // damping 0,62 · risposta 0,36 s (FluidMotion.MenuOpenDamping / MenuOpenStiffness)
  val House: AnimationSpec<Float>    // rincorsa 180 ms + molla 0,58
}
```

`Spring` ha gli stessi token del menu che si apre perché una superficie che si trasforma col proprio orologio e una guidata
dal menu devono muoversi uguale, o lo stesso tasto si apre in due modi (le gocce di Kyant viaggiano su molle con smorzamento 0,6–0,7). La soglia di
visibilità è 0,001 (un millesimo di viaggio). **Attenzione**: il viaggio *del menu* (`FluidMenuJourney`) non passa da
`morphTo` ma da `driveExternally` con la molla della goccia (`FluidMotion.dropOpen`/`dropClose`): vedi [menu-modali.md](menu-modali.md).

## Il tasto che si apre nel proprio menu

**Cos'è.** `FluidMorphMenuButton` è la capsula di vetro che **si espande** nel proprio menu contestuale — la prima
integrazione vera del motore. Il predecessore (`FluidGlassMenuButton`) fa comparire il pannello *sopra* il tasto: due
superfici, una staffetta. Qui la superficie è **una**: la capsula viaggia fino a essere il pannello e al congedo torna a essere il tasto. L'etichetta del tasto se ne va con l'inizio del viaggio, le voci arrivano attaccate alla superficie, niente si stira.

**Firma essenziale.**

```kotlin
@Composable fun rememberFluidMorphMenuState(): FluidMorphMenuState
val LocalFluidMorphMenuState: ProvidableCompositionLocal<FluidMorphMenuState?>   // 3.0.1: per chi sta lontano dalla radice

@Composable fun FluidMorphMenuButton(
  state: FluidMorphMenuState = LocalFluidMorphMenuState.current ?: rememberFluidMorphMenuState(),
  actions: () -> List<FluidContextAction>, onClick: () -> Unit, modifier: Modifier = Modifier,
  text: String? = null, icon: ImageVector? = null, backdrop: GlassBackdropState,
  enabled: Boolean = true, opensOnTap: Boolean = false,
)
@Composable fun FluidMorphMenuHost(state: FluidMorphMenuState, backdrop: GlassBackdropState, modifier: Modifier = Modifier)

class FluidMorphMenuState {
  var isOpen: Boolean; var isOnScreen: Boolean         // sola lettura per i chiamanti
  fun dismiss()
  fun open(bounds: Rect, text: String?, icon: ImageVector?, items: List<FluidContextAction>, content: (@Composable () -> Unit)? = null)
}
```

**Uso** (`catalog/MenusDemo.kt`, `GalleryActivity.kt`):

```kotlin
val menu = rememberFluidMorphMenuState()
CompositionLocalProvider(LocalFluidMorphMenuState provides menu) {
  App()                                                       // da qualsiasi profondità: FluidMorphMenuButton(...)
  FluidMorphMenuHost(state = menu, backdrop = backdrop)       // alla radice, SOPRA il contenuto
}
// nella schermata:
FluidMorphMenuButton(
  actions = { listOf("Per data", "Per nome").map { l -> FluidContextAction(label = l, selected = l == order) { order = l } } },
  onClick = {}, text = "Ordina", backdrop = backdrop,
  opensOnTap = true,                                           // per un tasto il cui unico mestiere è il menu
)
```

**Comportamento.**
- **Architettura portale/host**, come i modali: l'ancora vive nel layout e riporta i propri limiti; la superficie che si
  trasforma vive nell'host alla radice, perché un menu deve crescere oltre i limiti del proprio genitore e l'unico posto dove il
  vetro può farlo restando vetro è dentro la composizione, non in una finestra. L'host a riposo compone un nodo vuoto (niente
  gesti, niente disegno) che tiene le misure per l'apertura sincrona.
- **Si sceglie col dito che l'ha aperto** (3.0): tieni premuto → il menu si apre (un `Open`, uno solo, e solo se si apre
  davvero) e il dito ci passa sopra, una goccia di vetro lo segue di voce in voce con un tick per voce, il rilascio su una
  voce la sceglie (un `Tap`). Un rilascio fermo, fuori dalle voci, su un separatore o su una voce spenta **lascia il menu
  aperto**: si sceglie allora con un tocco. Tocco su `FluidMorphMenuButton`: `onClick`, a meno di `opensOnTap`, che apre al
  tocco (suona `Open`) e non chiama `onClick`. Il tenuto premuto apre **in ogni caso**.
- Chiude: il back (`BackHandler`), un tocco fuori **ovunque sullo schermo** (via le fasce dell'host dei modali attorno ai limiti
  dell'host, anche se non è a schermo intero), una scelta, `dismiss()`. Il tasto esce dalla composizione col menu aperto: il menu se ne va con lui.
- **L'apertura è sincrona**: il gesto risolve il pannello con le misure che l'host scrive a ogni layout, installa il
  viaggio e nasconde il tasto *nello stesso istante*; la superficie compare sul pixel del tasto nel fotogramma in cui il tasto
  smette di disegnarsi (`drawWithContent { if (!hidden) drawContent() }`: cambio di proprietario, non un'uscita; mai alpha di un layer sopra il vetro). Il tasto riappare quando la superficie è tornata capsula **e la goccia si è posata** (`finishClose`).
- Il viaggio parte dal tasto **come lo sta gonfiando il dito** (sagoma premuta); la pelle e la lente passano dal tasto al
  pannello (`tintFrom`/`opticsFrom` = `GlassDefaults.controlTint()`/`buttonOptics()`; al 60% del viaggio è già il pannello); il
  pannello è `GlassRole.Modal` con `GlassDefaults.panelOptics(compact = true)` e `panelTint()`. Posizione: sopra il tasto se c'è
  spazio (un menu che nasce da un tasto in basso cresce verso l'alto), altrimenti sotto, dentro i margini; si allarga per la
  voce più lunga fino a 340 dp (3.0.1). La faccia del tasto è spenta entro il 35% del viaggio, le voci entrano dal 40% al 75% in
  apertura (0,40–0,75 dalla 3.0.4: apriva lento) e se ne vanno per prime in chiusura (0,94–0,62).
- Il tasto: capsula alta 44 dp con 20 dp di padding orizzontale, `glassControlSurface` + `fluidPressable` con `FluidPressFeedback.Expand` (la capsula cresce e si illumina), gesto dei tasti (`Role.Button`).
- Il colore del testo sul pannello è `onSurface` (l'host sta alla radice fuori da una `Surface`, dove `LocalContentColor` è nero: 3.0.1).
- `FluidMorphMenuState.open(bounds, …)` è pubblico (1.13.0): apre **senza il dito**, e il menu si sceglie con un tocco.
  Chi lo chiama da un'ancora propria deve anche nascondersi mentre `isOnScreen` è vero. `bounds` sono pixel della radice;
  `content` (la faccia dell'ancora) deve essere inerte.

**Trappole.**
- **Senza un host composto non apre niente e non vibra** (3.0.0: prima l'host si misurava da sé a ogni apertura e il tasto si
  nascondeva per sempre). Senza un fornitore né uno stato passato il tasto fa il suo `onClick` e il menu non si apre; con
  `opensOnTap = true` e senza host, nemmeno `onClick`.
- L'host a schermo parziale (un palco della galleria) è permesso: il menu cresce dentro i suoi limiti, e il tocco fuori chiude lo stesso.
- Un solo menu in scena: se ne è già aperto un altro (o `items` è vuoto) il gesto non apre niente e non vibra; riaprire dallo stesso tasto un menu che si
  sta chiudendo è un'**inversione** (il viaggio torna indietro dal punto in cui è, con la sua velocità).

**Non usarlo quando.** Il tasto ha un solo mestiere e va aperto da un componente della barra: `FluidBarAction(actions)` (stessa
finestra, senza host da montare). Per il menu di una riga: `FluidListRow(contextActions)` / `fluidContextMenu`
([menu-modali.md](menu-modali.md)).

**Versione.** Dalla 1.9.4; `open` e `driveExternally` pubblici 1.13.0; `opensOnTap`, dito che sceglie, goccia: 3.0.0;
`LocalFluidMorphMenuState`: 3.0.1; apertura più veloce: 3.0.4.

## Costi e tier

**`FluidPhysicsTier`** — quanto materiale un morph può portare su *questo* dispositivo, adesso. La **geometria del viaggio è
identica sui tre livelli**; cambia solo quanta ottica la veste:

| tier | quando | cosa disegna |
|---|---|---|
| `Full` | SDK 33+ (`RuntimeShader`) e shader accettati dal driver | col materiale nuovo (di serie, dove le sue sorgenti compilano e nessuno chiede `Classic`): il corpo di `glassSurface` con luce e ombra nel corpo, fermo e in viaggio, collo solo in viaggio. Col vetro di prima: rifrazione SDF per ogni famiglia, ponte liquido fra i pezzi (anche a riposo), goccia con lente in moto, frangia dove l'ottica la chiede. In tutti e due la silhouette la ritaglia lo shader |
| `Balanced` | SDK 31–32, o uno shader che il driver ha rifiutato | sfocatura, vividezza, tinta (path) e bordo; **senza** lente, ponte, frangia. Il clip del layer torna la sagoma esatta (più caro, su un morph transiente) |
| `Lite` | sotto SDK 31 | niente fondale: la tinta solida di ripiego (`tint.fallback`), ritagliata sul path che si trasforma |

- **Il tetto del dispositivo vince sempre**: `fluidPhysicsSurface(tier = …)` può solo *scendere* (`FluidPhysicsTierTest`:
  una richiesta più ricca del tetto non è un'opinione, è un crash). `null` = il tetto.
- Il tetto lo decide anche una **compilazione di prova una volta per processo** dei due shader (gruppo e poligono) con loop
  fino a 64/96 lati: AGSL legittimo non vuol dire accettato da ogni driver. Se il driver rifiuta, il livello si ferma a `Balanced`
  e in logcat restano due righe a livello errore: una col tag `FluidGlass`, il nome della sorgente e il messaggio del
  compilatore, una col tag `FluidPhysics` che dice a che livello si è fermato il motore (`adb logcat -s FluidGlass
  FluidPhysics`: sono le righe da cercare quando un telefono «non ha il vetro»). Fino alla 3.1.2 era un solo avviso, ed è
  così che una parola riservata in uno shader ha tenuto tutto il motore a `Balanced` per tutta la 3.x: vedi la scheda del
  motore. La frangia ha la sua sonda a parte: un driver che la rifiuta toglie solo la frangia.
- Costi veri: un fotogramma di morph = uniform nuovi su shader compilato + un passaggio GPU; **a riposo zero**, la goccia non
  chiede fotogrammi; un gruppo costa `pezzi × pixel` (max 8); una sagoma libera ha un loop di fino a 96 vertici (64 quella
  disegnata a mano o preset; 96 l'anello della goccia); la sfocatura costa quanto in `glassSurface` e la **cattura del
  fondale si fa una volta** (mai una ri-cattura durante il viaggio: il padding resta zero e fermo).
  Il primo giro dopo un'installazione compila le varianti nuove degli shader (la sorgente è fissa: la cache è per processo).
  Il costo delle *sorgenti* di fondale a schermo intero (che saturano il RenderThread) è della famiglia vetro, non di questo motore.

## Le tre trappole del vetro in movimento

Pagate una per una sui fotogrammi, migrando la card che si espande (1.9.7). Il testo di riferimento è in
[../regole.md](../regole.md), «Tre trappole del vetro in movimento». Chi tocca una superficie di vetro animata le incontra tutte e tre:

1. **Mai scalare il layer di una superficie di vetro per farle fare un rimbalzo.** `graphicsLayer { scaleX = … }` scala anche il
   fondale campionato dentro il vetro: il testo della pagina *dietro* si muove, e quel testo sta fermo. L'oltrepasso va nella
   **geometria della sagoma** (gonfiore, `overshootInflation`), non nella trasformazione del nodo. (Ne discende: «nessun layer scalato sul vetro» nel menu contestuale, dove scala solo l'immagine registrata della riga, che non è vetro.)
2. **Il primo e l'ultimo fotogramma di vita di un nodo possono disegnare con le proprietà di default.** Su una superficie il
   cui arrivo è un alpha (scrim, finestra dei modali) è un lampo a piena forza (misurato: un fotogramma a luminanza 190 in mezzo a una serie a 214). Il cancello è `drawWithContent { if (amount > 0.004f) drawContent() }`: a materiale zero non si registra niente. `fluidPhysicsSurface` lo fa con `intensity` (soglia 0,001).
3. **Gli `exports` di uno scrim registrano il suo materiale a forza PIENA**, perché il suo arrivo è un alpha sul pannello finito:
   chi campiona il composito resta scuro mentre lo scrim vero sfuma. Meglio campionare la pagina cruda e dipingersi la
   propria quota di scurimento alla forza corrente (`tintFrom` + `tintBlend`). In `fluidPhysicsSurface` `exports` segue `intensity`; un alpha messo da fuori no.

Altre trappole specifiche di questa famiglia, già riportate sopra: il **drive esterno che non si toglie**, `isMorphing` falso con
`driveExternally`, la `progress` della prima chiamata che resta, i due ruoli di `fluidPhysicsContent` da scambiare, e
**mai un'ombra di piattaforma** sotto il vetro.

## Giudicare dai fotogrammi: la ricetta di QA

Nessuna delle tre trappole si vede «ragionando», e **«è giusto per costruzione» qui non è mai bastato**: gli host dei test non
hanno fondale (ripiegano sul pannello opaco), quindi **nessun test dice com'è il vetro**: la goccia sul vetro, il cambio di
proprietario fra tasto e finestra, il lampo di un fotogramma si guardano in mano. Fonte: fine di [../regole.md](../regole.md), `tools/qa/`, `docs/riferimenti-3.0.md`.

1. **Costruire, installare, guardare** sul dispositivo (S25, Tab S9 o l'emulatore `Medium_Phone`, 1080×2400, API 37), con la
   build che si installa davvero (una debug non si installa sopra quella firmata). Prima dell'esito, mai dopo.
2. **Registrare** con `screenrecord` (`qa_record NOME SECONDI` in `tools/qa/qa-common.sh`: `--bit-rate 20000000`, un secondo di
   anticipo prima del gesto). **Estrarre con `-fps_mode passthrough`**: `fps=30` su un video a frame rate variabile riordina e
   duplica i fotogrammi, e una cronologia sbagliata porta a diagnosi sbagliate. `qa_finish` scrive i fotogrammi a metà
   risoluzione (`scale=iw/2:-1`, `%04d.png`) e la luminanza per fotogramma (`signalstats,metadata=print`).
3. **Fotogrammi densi a 30 fps, e guardare i primi cinque.** A 30 fps un fotogramma dura ~33 ms: l'apertura di un menu
   (~267 ms di moto dalla 3.0.4) sono 8 fotogrammi, quindi i primi cinque sono *quasi tutta* la partenza, dove stanno i
   difetti (il salto, il lampo, il vuoto senza né tasto né menu). Mai «giusto per costruzione».
4. **Un collage** dei fotogrammi del viaggio (a mano o con un piccolo script: un foglio, il numero del fotogramma in un angolo)
   si legge meglio di una pagina di PNG. Per vedere la *forma* rallentare con la scala animazioni di sistema
   (`animator_duration_scale 5`, le molle di Compose e la goccia la rispettano: il viaggio dura cinque volte tanto) e
   rifare la prova a 1× per i tempi veri. **Riportare la scala a 1** a fine prova.
5. **Per un difetto di un solo fotogramma la misura batte l'occhio**: `signalstats,metadata=print` dà la luminanza per
   fotogramma, e un lampo è un **picco isolato in una serie che dovrebbe essere monotona**. Un `diff.py` (differenza media fra un
   fotogramma e il precedente) trova dove stanno le transizioni.
6. **Cosa cercare**: il primo fotogramma è la lastra del mittente (niente salto alla partenza); la sagoma arriva alla lastra
   senza gradino (bordo e ombra compresi); tasto e menu non hanno un fotogramma di vuoto né due insieme; il testo dietro il
   vetro **non si muove** (trappola 1); nessun lampo (trappola 2); lo scrim non lascia il composito scuro (3); in chiusura le
   voci seguono la sagoma e non galleggiano sulla pagina; nessun doppio bordo in una fusione.
7. **Due corse per scenario con gesti identici** (`run1`/`run2`, come in `tools/qa/baseline.sh`: `pill_drag`, `morph_menu`,
   `context_menu`, `expand_card`, `back_gesture`, `playground`): una corsa sola non distingue un difetto da un caso. La dipendenza dal dito si
   riproduce con un lungo tocco da `adb shell input swipe x y x y 900`.

## Token, costanti, Defaults

| cosa | valore | dove |
|---|---|---|
| `FluidForm.Group.MaxPieces` | 8 (6 fino alla 3.x) | `FluidForm.kt` (e `PhysicsMaxPieces` dello shader e `GlassFieldMaxPieces` del campo, devono restare uguali) |
| `FluidForm.Group.blendRadius` di serie | 48 px | idem |
| `FluidFormPresets.MaxVertices` / `FreehandVertices` | 64 / 48 | `FluidFormPresets.kt` |
| `FluidPhysicsJourney.Spring` | damping `FluidMotion.MenuOpenDamping` 0,62; risposta 0,36 s | `FluidPhysicsJourney.kt` |
| `FluidPhysicsJourney.House` | rincorsa 180 ms fino all'80%, poi molla 0,58 / risposta 0,20 s | idem |
| gonfiore oltre l'1 | guadagno `FluidMotion.MenuInflationGain` 0,6; max 8%; tetto 12 dp per lato | idem |
| velocità ereditata | −3 … +6 viaggi/s | idem |
| soglia di visibilità della molla | 0,001 | idem |
| goccia | `FluidMotion.DropPoints` 48 punti; `DropStiffnessNear` 1500, `DropStiffnessFar` 320; `DropDamping` 0,72; `DropEdgeTensionDefault` 0,2 (di `DropEdgeTensionReference` 700); `DropAnchorReachDp` 110; passo 1/240 s, fotogramma al più 1/30 s; posa sotto `DropRestPx` 0,25 px e `DropRestSpeedDp` 3 dp/s | `FluidMotion.kt` |
| lente in moto | `FluidMotion.LiquidLensFullSpeedDp` 700 dp/s, seguita in `LiquidLensFollowSeconds` 0,06 s | idem |
| contenuto del menu | entra `LiquidContentOpenFrom/To` 0,4–0,75; esce `LiquidContentCloseFrom/To` 0,62–0,94; faccia del tasto spenta a `MenuFaceTo` 0,35 | idem |
| anello della goccia disegnato | 96 punti (`DropRenderPoints`, interno) | `FluidPhysicsLiquid.kt` |
| `LocalFluidPhysicsLiquid` | `true` | `FluidPhysicsLiquid.kt` (debug) |

Nessun oggetto `…Defaults` in questa famiglia: i numeri stanno in `FluidMotion` (token) e nelle costanti interne di
`FluidPhysicsJourney.kt`. Le costanti `internal` non sono API: se un'app ne ha bisogno, si chiede un token.

## Cosa è verificato dai test

159 metodi di test JVM in `engine-ui/src/test/kotlin/dev/antigravity/fluidengine/ui/fluidphysics/`, più la serie di
robustezza (946 prove parametriche), e la parità del materiale in `…/ui/glass/field/` (nessuno esegue AGSL: gli shader
sono provati via gli specchi Kotlin del campo di distanza, sul banco e, sul dispositivo, a fotogrammi):

- `GlassRecipeParityTest` (in `ui/glass/field/`): una superficie fisica ferma col materiale nuovo riceve gli stessi numeri
  di corpo, luce e ombra della `glassSurface` della stessa forma (tasto, capsula, pannello; due temi, due densità,
  intensità, qualità, pellicola); l'arrivo di un viaggio è la `glassSurface` della forma d'arrivo; il primo fotogramma
  è la superficie ferma, anche nel velo dei grandi sul tema scuro (`PhysicsLargeVeil`); sotto Android 13,
  con `Classic` o con un livello chiesto sotto `Full` la superficie non prende il materiale nuovo.
- `PhysicsLiquidTest`, `F1b2GeometryTest`: il livello e il materiale come tabella di verità; il piano porta otto pezzi; a
  riposo i pezzi sono separati e il collo vive solo in viaggio; la ricetta di un viaggio coincide ai capi con quella
  della lastra ferma; il margine della cattura non si muove; la luce è sempre nel corpo; il ritaglio emulato segue quello
  di una `glassSurface`; la posa della goccia e le cuciture della fusione.
- `MorphRobustnessTest`, `MorphRobustnessDiagnosticsTest`: la trasformazione universale su una serie fissa di 43 coppie di
  forme (catalogo `engine-ui/src/robustness/`, lo stesso della pagina «Robustezza» della galleria), interrotta, invertita,
  ridiretta, a 60 e 120 Hz: nessuna eccezione, nessun valore non finito, contorno senza incroci, area e baricentro senza
  salti, materiale continuo, arrivo esatto. Dieci prove note e dichiarate (la goccia nel 24 ↔ 600 e verso un lato zero).

- `RestStateDisciplineTest`, `FluidPhysicsCancelTest`: a riposo piano e `Shape` sono la **stessa istanza** a ogni fotogramma, in viaggio
  ogni progresso è un'istantanea nuova; gli estremi di un transito sono le forme; ogni viaggio, anche i duri via scalo, atterra senza eccezioni;
  un viaggio cancellato si ferma dove si trova.
- `FluidPhysicsJourneyTest`, `MorphOvershootTest`: la molla di serie parte subito, oltrepassa e si posa; `House` parte piano e atterra esatta;
  la velocità ereditata è la proiezione sul viaggio nuovo e sta nei suoi tetti; l'oltrepasso è gonfiore uniforme, con tetti in frazione e in pixel,
  e una fusione oltre l'1 è un contorno solo.
- `FluidPhysicsMaterialTest` (il **vetro di prima**): a riposo la lente è esattamente quella che il vetro di prima di
  `glassSurface` calcola per ogni ottica di serie; la miscela è esatta ai capi e il margine di cattura non si muove mai;
  cupola e frangia sfumano.
- `LiquidDropTest`, `DropPlanTest`, `DropRimTest`: la coda resta indietro di `2ζu/√kMin`, accanto all'ancora nessun ritardo, **zero esatto
  alla posa e nessun fotogramma chiesto**, il piano della posa è la lastra al bit, il tempo è quello delle animazioni, il bordo ha il profilo della lastra.
- `ExternalDriveStartFrameTest`, `ExternalGroupDriveTest`: la partenza viva, la cache del piano, il drive fra gruppi (due tasti→tre, tre→semi, sei per parte).
- `PieceMatchingTest`, `MatchedRingsTest`, `FluidFormGeometryTest`, `PolySdfMathTest`, `SlabParamLerpTest`: accoppiamento deterministico senza orfani;
  anelli con la stessa parametrizzazione entro il budget; RDP, ricampionamento, verso, tratto degradato a cerchio; segno e distanza del campo; raggi mai oltre mezzo lato.
- `FluidPhysicsTierTest`: la tabella di verità del tetto e «una richiesta può solo scendere». (Contiene anche il test di posizione del pannello del menu.)

**Non provato da nessun test**: com'è il vetro (rifrazione, bordo e ombra a schermo), la goccia sul fondale, lo stacco fra tasto e
finestra, il `FluidMorphMenuButton` col dito (lo coprono `FluidMenuHandOffTest` e `FluidButtonDragTest` nel `sample`, senza fondale), l'apertura sincrona senza fotogramma morto, `opensOnTap` (solo la demo).

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluidphysics/`) | cosa c'è |
|---|---|
| `FluidForm.kt`, `FluidFormPresets.kt` | `FluidForm` (`Slab`/`Poly`/`Group`), `FluidCornerRadii`, i preset e `fromFreehand` |
| `FluidPhysicsState.kt` | lo stato, `morphTo`/`snapTo`/`driveExternally`, i transiti, il piano di resa (`PhysicsRenderPlan`) |
| `FluidPhysicsSurface.kt` | `fluidPhysicsSurface`, `fluidPhysicsContent`, il ripiego `Lite`, il bagliore del dito |
| `FluidPhysicsJourney.kt` | `Spring`, `House`, velocità ereditata, gonfiore dell'oltrepasso |
| `FluidPhysicsLiquid.kt` | la goccia (`LiquidDrop`), il perimetro, l'anello disegnato, la lente in moto |
| `FluidPhysicsTier.kt` | `FluidPhysicsTier`, la tabella di verità, la sonda degli shader |
| `FluidPhysicsClip.kt`, `FluidPhysicsShape.kt` | `fluidPhysicsClip` e il patto col clip hardware |
| `FluidMorphMenu.kt` | `FluidMorphMenuState`, `FluidMorphMenuButton`, `FluidMorphMenuHost` |
| `FluidPhysicsMaterial.kt`, `FluidPhysicsEffects.kt`, `FluidPhysicsShaders.kt`, `FluidPhysicsGeometry.kt`, `FluidPhysicsRings.kt`, `FluidPhysicsRim.kt`, `FluidPhysicsShadow.kt` | il motore: vedi [fluid-physics-motore.md](fluid-physics-motore.md) |
| `sample/.../playground/` (`PlaygroundStage.kt`, `PlaygroundTab.kt`, `PracticalDemos.kt`), `sample/.../catalog/MenusDemo.kt` | i call-site veri: palco, forme, due tasti→menu→pop-up, il tasto-menu |
| `tools/qa/` | `qa-common.sh`, `baseline.sh`: registrazione, fotogrammi, luminanza |
