package dev.antigravity.fluidengine.ui.fluidphysics

import dev.antigravity.fluidengine.ui.fluid.GlassTint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.translate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.addContinuousRoundRect
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassSlabGeometry
import dev.antigravity.fluidengine.ui.glass.field.glassSlabGeometry
import dev.antigravity.fluidengine.ui.glass.field.glassSlabIsSmall
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

/**
 * Il cuore di Fluid-physics: possiede il viaggio fra due [FluidForm].
 *
 * Il contratto di performance che tutto il sottosistema esiste per onorare: **un fotogramma di
 * morph è nuovi uniform su uno shader già compilato, più un passaggio GPU.** Mai una ri-cattura del
 * fondale, mai un re-clip del layer, mai un cambio di padding. Le uniche cose che cambiano per
 * fotogramma sono gli array di questo piano e l'istantanea di sagoma — e a riposo nemmeno quelle:
 * a progress 0 e 1 la superficie tiene un'istanza stabile, così uno scorrimento con un pezzo fermo
 * sopra non paga un centesimo del morph. È la risposta alla regola "niente shape morphing" di
 * [dev.antigravity.fluidengine.ui.fluid.FluidInteractions]: il morphing è transiente per contratto.
 *
 * L'altra lezione incorporata è quella del design rigettato (`FluidPopoverMorphWindow`, CHANGELOG
 * 1.8.3/1.8.5): questo stato trasforma **silhouette e materiale**, mai il contenuto. Il contenuto
 * segue le sue regole — layout alla taglia d'arrivo, dissolvenza, zoom uniforme — attraverso
 * `Modifier.fluidPhysicsContent`.
 *
 * `progress` non è vincolato a 0..1: le molle hanno overshoot, e l'overshoot è la vita del morph.
 * Dalla 3.0 oltre l'1 la sagoma NON prosegue lungo il viaggio (fianchi in dentro e fondo in fuori,
 * se il viaggio stringeva): è il traguardo gonfiato attorno al proprio centro, uniforme, con un tetto
 * ([morphOvershootScale]). Sotto lo 0 — una partenza che eredita una velocità all'indietro — la
 * sagoma prosegue per la strada che aveva, ed è giusto: è il moto di prima che si esaurisce. I limiti
 * fisici (lati minimi, raggi fra zero e mezzo lato) li tengono le funzioni di interpolazione.
 */
@Stable
class FluidPhysicsState internal constructor(initial: FluidForm) {

  private var formState by mutableStateOf(initial)

  /** La forma a cui la superficie è arrivata, o verso cui sta viaggiando. */
  val form: FluidForm get() = formState

  private var activeTransit by mutableStateOf<Transit?>(null)

  /**
   * 0..1 lungo il viaggio corrente (con l'overshoot della molla), 1 a riposo. Da leggere in fase
   * di disegno — dentro `graphicsLayer {}` o un draw block — mai in composizione: è lo stato che
   * cambia a ogni fotogramma.
   */
  val progress: Float
    get() = planeDrive?.progress?.invoke() ?: externalDrive?.progress?.invoke() ?: activeTransit?.progress?.value ?: 1f

  private var externalDrive by mutableStateOf<ExternalWindowDrive?>(null)

  /** La guida «a fotogramma» del piano flottante, se c'è: vedi [driveByFrame]. */
  private var planeDrive by mutableStateOf<PhysicsPlaneDrive?>(null)

  /**
   * Mette lo stato al servizio del **piano flottante** (F3.2c, `FluidGlassContainer`): la sagoma di ogni fotogramma è
   * [frame], letta in disegno, già calcolata dal piano (accoppiamento, nascite, comparse, molle per lato, interruzioni
   * con la velocità di ogni lato) col suo collo, le sue unioni, la sua comparsa e il peso del vetro in viaggio.
   *
   * Perché non [driveExternally]: quella rifà l'accoppiamento (al costo minimo, che può non essere quello del piano) e
   * porta un progresso solo; chiamata a ogni fotogramma con la geometria del piano allocava una guida, un transito e il
   * suo `Animatable`, e azzerava la cache del piano. Qui la guida si installa **una volta** e le lambda si sostituiscono
   * sul posto; il piano di resa si rifà solo quando [frame] restituisce una forma diversa (per identità: chi guida ne
   * crea una nuova solo quando cambia), e a riposo resta la stessa istanza.
   *
   * [progress] dice a chi lo chiede quanto del viaggio è passato: all'uno la superficie è ferma. Niente goccia: il moto
   * del piano è già una molla per lato, e il vetro in viaggio lo dà [PhysicsPlaneShape.motion].
   */
  internal fun driveByFrame(frame: () -> PhysicsPlaneShape, progress: () -> Float) {
    val current = planeDrive
    if (current != null) {
      current.frame = frame
      current.progress = progress
      return
    }
    externalDrive = null
    activeTransit = null
    planeDrive = PhysicsPlaneDrive(frame, progress)
    planePlanShape = null
  }

  /** La forma del piano flottante di questo istante, o null se lo stato non è guidato dal piano. Fase di disegno. */
  internal fun planeShapeNow(): PhysicsPlaneShape? = planeDrive?.frame?.invoke()

  /**
   * Mette lo stato al servizio di un orologio ALTRUI: la silhouette è il lerp [from]→[to] al
   * [progress] fornito, e morphTo/snapTo non c'entrano più.
   *
   * Esiste per i componenti che un orologio ce l'hanno già — il modale, con le sue molle legate a
   * dissolvenze, scrim e ritirata del back predittivo — e a cui serve solo la *finestra* di
   * Fluid-physics: la sagoma che viaggia con la rifrazione addosso. Due orologi sulla stessa
   * superficie sarebbero un disallineamento garantito; qui l'orologio resta uno, il loro.
   */
  /**
   * Il gancio per un componente che un orologio ce l'ha gia': la fisica diventa la *finestra* e il
   * progresso resta quello delle molle del chiamante — due orologi sulla stessa superficie sono un
   * disallineamento garantito. Pubblico dalla 1.13.0: e' cosi' che un'app aggancia il viaggio
   * pill→player al proprio `Animatable`, drag compreso. Il drive resta installato finche' la
   * superficie vive; richiamarlo con gli stessi estremi e' gratis.
   */
  fun driveExternally(
    from: FluidForm.Slab,
    to: FluidForm.Slab,
    progress: () -> Float,
    /**
     * Come rendere l'oltrepasso della molla (progress > 1). A zero, il lerp estrapola oltre il
     * traguardo NELLA DIREZIONE del viaggio — che per una riga larga che diventa una card
     * stretta significa fianchi in dentro e fondo in fuori. Con un valore positivo, oltre l'1
     * la sagoma e' il traguardo GONFIATO attorno al proprio centro:
     * (progress − 1) · overshootInflation e' la frazione di gonfiore per asse. I due assi sono
     * separati perche' hanno vincoli diversi: lo schermo limita i fianchi, non il fondo — il
     * chiamante puo' smorzare la X senza togliere vita alla Y.
     */
    overshootInflationX: Float = 0f,
    overshootInflationY: Float = 0f,
  ) {
    driveExternally(from, to, progress, overshootInflationX, overshootInflationY, startFrame = null)
  }

  /**
   * Come [driveExternally], con un fotogramma di PARTENZA vivo (3.0, per i menu).
   *
   * Un'ancora che si apre in un menu non parte da dove sta a riposo: parte da dove il dito l'ha
   * spinta (il tasto premuto e' piu' grande) e, mentre il viaggio gia' corre, la pressione si scioglie
   * e il fotogramma di partenza torna a riposo. Un `from` fisso farebbe un salto a ogni cambio di
   * quel fotogramma; con [startFrame] il viaggio e' il lerp fra il rettangolo di QUESTO istante e
   * [to], e la partenza si muove sotto la sagoma senza che si veda.
   *
   * Con [startFrame] presente `from` non decide la geometria (resta il segnaposto per il confronto
   * "stessi estremi"): la partenza e' una capsula (raggio mezzo lato corto) sul rettangolo che la
   * lambda restituisce, letta in fase di disegno — puo' leggere stati, e il piano si tiene in
   * cache su (t, rettangolo), quindi a partenza ferma costa quanto prima. Richiamare con gli stessi
   * estremi e una lambda diversa la sostituisce sul posto, gratis.
   *
   * Con [travelSlab] il chiamante può fornire il contorno già interpolato, per esempio se posizione,
   * taglia e raggi devono avere tempi diversi. La goccia segue quello stesso contorno.
   *
   * Con [travelWarp] (4.0, la nascita del menu contestuale) il chiamante deforma quel contorno ([FluidDropWarp]):
   * coda, punta, vita, pancia e la lente della goccia. La stessa deformazione vale per la molla della goccia, per
   * l'anello disegnato, per il ritaglio del contenuto e per la lente; null, o una deformazione che non cambia
   * niente, e' il comportamento di sempre al bit. Non vale col movimento ridotto, ne' senza goccia (a riposo, o
   * dove il liquido e' spento): li' la sagoma e' la lastra di [travelSlab].
   *
   * Interna: l'API pubblica di Fluid-physics non cambia firma.
   */
  internal fun driveExternally(
    from: FluidForm.Slab,
    to: FluidForm.Slab,
    progress: () -> Float,
    overshootInflationX: Float = 0f,
    overshootInflationY: Float = 0f,
    startFrame: (() -> Rect)?,
    travelSlab: (() -> FluidForm.Slab?)? = null,
    travelWarp: (() -> FluidDropWarp?)? = null,
  ) {
    val current = externalDrive
    if (current != null && current.from == from && current.to == to &&
      current.overshootInflationX == overshootInflationX &&
      current.overshootInflationY == overshootInflationY &&
      (current.startFrame != null) == (startFrame != null)
    ) {
      // Stessa corsa. Se il chiamante ha ricreato la lambda della partenza (cattura valori nuovi) la
      // si prende: la cache del piano e' sul rettangolo, non sull'identita' della lambda, quindi
      // un cambio di partenza si vede al disegno successivo senza reinstallare nulla.
      if (startFrame != null) current.startFrame = startFrame
      current.travelSlab = travelSlab
      current.travelWarp = travelWarp
      return
    }
    planeDrive = null
    externalDrive = ExternalWindowDrive(
      transit = SlabTransit(listOf(from to to), blendRadius = 0f),
      from = from,
      to = to,
      progress = progress,
      overshootInflationX = overshootInflationX,
      overshootInflationY = overshootInflationY,
      startFrame = startFrame,
      travelSlab = travelSlab,
      travelWarp = travelWarp,
    )
    transitPlanStamp = Float.NaN
    transitPlanStart = null
  }

  /**
   * Come [driveExternally] fra due lastre, fra due forme di **sole lastre**: una lastra o un gruppo
   * di lastre per parte (3.0, la barra ancorata delle pagine).
   *
   * E' il viaggio di [morphTo] fra gruppi — gli stessi accoppiamenti al costo minimo, lo stesso ponte
   * di materiale fra pezzi vicini — con l'orologio del chiamante: la barra delle pagine lo guida col
   * progresso del dito, e due orologi sulla stessa superficie sarebbero un disallineamento garantito.
   * Un pezzo che non ha un compagno dall'altra parte lo riceve dal chiamante come **seme**: una lastra
   * di un pixel al centro del pezzo, cosi' nasce (o si spegne) sul posto invece di attraversare la
   * barra per raggiungere il compagno piu' vicino.
   *
   * Nessun oltrepasso: oltre l'1 la forma e' il traguardo fermo. Una sagoma libera ([FluidForm.Poly])
   * non e' ammessa: il campo fuso dei gruppi parla solo rettangoli arrotondati.
   */
  fun driveExternally(
    from: FluidForm,
    to: FluidForm,
    progress: () -> Float,
  ) {
    if (from is FluidForm.Slab && to is FluidForm.Slab) {
      driveExternally(from, to, progress, 0f, 0f, startFrame = null)
      return
    }
    val fromSlabs = requireNotNull(slabPiecesOf(from)) { "driveExternally fra gruppi vuole solo lastre: $from" }
    val toSlabs = requireNotNull(slabPiecesOf(to)) { "driveExternally fra gruppi vuole solo lastre: $to" }
    val current = externalDrive
    if (current != null && current.from == from && current.to == to && current.startFrame == null) return
    val blend = maxOf(groupBlendOf(from), groupBlendOf(to))
    planeDrive = null
    externalDrive = ExternalWindowDrive(
      transit = SlabTransit(matchSlabPairs(fromSlabs, toSlabs), blend),
      from = from,
      to = to,
      progress = progress,
      overshootInflationX = 0f,
      overshootInflationY = 0f,
      startFrame = null,
    )
    transitPlanStamp = Float.NaN
    transitPlanStart = null
  }

  /** Grossolano e sicuro da leggere in composizione: dietro c'è un derivedStateOf. */
  val isMorphing: Boolean by derivedStateOf { activeTransit != null }

  /** Aggiornato dalla composizione: con la scala animazioni a zero, [morphTo] arriva senza viaggiare. */
  internal var reducedMotion: Boolean = false

  /**
   * Pixel per dp, dalla composizione: tara il tetto del gonfiore oltre il traguardo
   * ([MorphInflationCapDp]). 1 in un test costruito a mano: il tetto e' allora in pixel.
   */
  internal var density: Float = 1f

  /**
   * La goccia della superficie (3.0, vedi FluidPhysicsLiquid.kt): la alimenta la superficie a ogni
   * fotogramma in cui la sagoma si muove ([runLiquid]), e il piano la disegna finche' non si e'
   * posata. Senza una superficie che la alimenti resta posata, e il piano e' quello di prima.
   */
  internal val liquid = LiquidDrop()
  internal var useSampledDropFrame = false
  private var sampledDropSlab: FluidForm.Slab? = null

  /** Il contorno ([FluidDropWarp]) su cui il passo ha integrato gli scarti, con [sampledDropSlab]: vanno in coppia. */
  private var sampledDropWarp: FluidDropWarp? = null

  /**
   * Fin dove arriva l'ancora della goccia, in dp: vicino all'ancora le molle sono rigide e seguono il moto della sagoma,
   * lontano morbide (la coda). Di serie [FluidMotion.DropAnchorReachDp]; la nascita del menu dal tasto (B1 a, una sagoma
   * netta e rapidissima) la allunga (`FluidButtonBirthProfile.DropReachDp`).
   */
  internal var dropAnchorReachDp: Float = FluidMotion.DropAnchorReachDp

  /** La tensione del bordo della goccia, frazione di quella del banco: la scrive la superficie. */
  internal var dropEdgeTension: Float = FluidMotion.DropEdgeTensionDefault

  /** Il contorno a riposo di questo istante, il perimetro e l'anello da disegnare: array riusati. */
  private val dropRest = FloatArray(FluidMotion.DropPoints * 2)
  private val dropPerimeter = RoundedRectPerimeter()
  private val dropParameters = FloatArray(DropRenderPoints)
  private val dropRing = FloatArray(DropRenderPoints * 2)
  // Quattro: il punto del perimetro (due) e il riquadro del ritaglio della goccia deformata ([dropInnerClip]).
  private val dropPoint = FloatArray(4)

  /** L'ancora dell'ultimo viaggio: la goccia che si posa dopo l'arrivo la tiene. */
  private var lastAnchor = Offset.Unspecified

  /**
   * Se la superficie vuole il liquido: falso con le animazioni ridotte, e falso finche' nessuna
   * superficie l'ha chiesto (uno stato costruito a mano in un test disegna la geometria di sempre).
   */
  internal var liquidAllowed: Boolean = false
    set(value) {
      if (field != value) {
        field = value
        if (!value) liquid.reset()
        transitPlanStamp = Float.NaN
        restPlanForm = null
        planePlanShape = null
      }
    }

  /**
   * Quante `fluidPhysicsShadow` disegnano gia' l'ombra di questo stato da fuori (il modale, fino
   * alla sua riscrittura). Finche' ce n'e' una, la superficie non disegna la propria: due ombre
   * sotto lo stesso vetro sono il doppio del peso di una lastra.
   */
  internal var externalShadows by mutableIntStateOf(0)

  /**
   * Vero se questo stato lo disegna il materiale nuovo (`Liquid`, F1B.1): allora l'ombra è nel corpo (F1B.2), una
   * sola, e né l'ombra a parte della superficie né `fluidPhysicsShadow` disegnano niente. Lo imposta la superficie
   * in composizione; si legge in disegno.
   */
  internal var liquidBody: Boolean = false

  /**
   * Se la silhouette la ritaglia lo shader (tier Full) o il clip del layer (tier Balanced).
   * Lo imposta la superficie, una volta: il tetto del tier è un fatto del processo, non cambia.
   */
  internal var maskInShader: Boolean = true
    set(value) {
      if (field != value) {
        field = value
        restPlanForm = null
        transitPlanStamp = Float.NaN
        planePlanShape = null
      }
    }

  private val restPlan = PhysicsRenderPlan()

  /** Il piano della guida del piano flottante e la forma con cui è stato costruito: vedi [driveByFrame]. */
  private val planePlan = PhysicsRenderPlan()
  private var planePlanShape: PhysicsPlaneShape? = null
  private var restPlanForm: FluidForm? = null
  private val transitPlan = PhysicsRenderPlan()
  private var transitPlanStamp = Float.NaN
  private var transitPlanTransit: Transit? = null

  /** Il rettangolo di partenza con cui e' stato costruito il piano esterno (null: partenza fissa). */
  private var transitPlanStart: Rect? = null
  private var transitPlanTravel: FluidForm.Slab? = null
  private var transitPlanWarp: FluidDropWarp? = null

  /** La versione del corpo elastico con cui e' stato costruito il piano in viaggio. */
  private var transitPlanLiquid = -1

  /**
   * Il collo di una forma **fermata a metà viaggio** (4.0): se un viaggio fra gruppi viene cancellato mentre i
   * pezzi sono uniti da un ponte, la forma resta dov'è con il ponte com'era, e il viaggio successivo lo eredita
   * ([travelFusion]). Zero per ogni forma arrivata o posata con [snapTo]: a riposo i pezzi sono separati.
   */
  private var heldFusion = 0f

  /**
   * Come [heldFusion], per il collo nel tempo (F1B.9): la coordinata del collo di un viaggio di [morphTo] fermato a
   * metà, che la forma ferma disegna e il viaggio dopo eredita. Null per ogni forma arrivata o posata con [snapTo].
   */
  private var heldNeck: Float? = null

  /** Il piano della forma ferma che ancora oscilla (il corpo che si posa dopo l'arrivo). */
  private val settlePlan = PhysicsRenderPlan()
  private var settlePlanForm: FluidForm? = null
  private var settlePlanLiquid = -1

  /**
   * Fa viaggiare la superficie verso [target] e sospende finché la molla non si è posata.
   *
   * Chiamarla mentre un viaggio è in corso è legittimo ed è un *ritargeting*: il nuovo viaggio
   * parte dalla geometria in cui il pezzo si trova adesso, non da dove sarebbe dovuto arrivare —
   * un dito che cambia idea non deve mai veder saltare la forma.
   *
   * Non esistono coppie vietate. Il solo viaggio che il renderer non sa fare in un colpo —
   * una sagoma libera da/verso un gruppo — diventa da sé un viaggio in **due tappe** passando per
   * lo scalo di [physicsLayoverFor]: i pezzi si fondono in un pannello sul footprint della sagoma,
   * e il pannello diventa la sagoma (o il percorso inverso). Un gesto legittimo non è mai
   * un'eccezione.
   */
  /**
   * Fa viaggiare la superficie verso [target] e sospende finché non si è posata.
   *
   * Con [spec] nullo — il default — il viaggio è **una molla sola**, [FluidPhysicsJourney.Spring]
   * (3.0): parte alla velocità del momento e si posa con un gonfiore uniforme attorno al centro del
   * traguardo. Fino alla 2.13 il default era la coreografia della casa (rincorsa piano→veloce, poi
   * una molla secca): resta, esplicita, come [FluidPhysicsJourney.House], per i morph dimostrativi
   * avviati da un tocco secco. Un [spec] qualsiasi scavalca il default.
   *
   * @param initialVelocity la velocità di partenza, in viaggi al secondo: quella di un gesto che il
   *   chiamante conosce (un dito che lancia il pannello). Null — il default — vuol dire "quella del
   *   viaggio che questo interrompe", proiettata sulla strada nuova ([inheritedProgressVelocity]):
   *   un ritargeting non riparte mai da fermo. Zero da una superficie ferma.
   */
  suspend fun morphTo(
    target: FluidForm,
    spec: AnimationSpec<Float>? = null,
    initialVelocity: Float? = null,
  ) {
    if (activeTransit == null && target == formState) return
    val layover = physicsLayoverFor(currentForm(), target)
    if (layover != null) {
      // La tappa di scalo è servizio, non spettacolo: criticamente smorzata e rapida, così il
      // viaggio si legge come UNO — fondersi e cambiare pelle — e non come due animazioni con
      // una pausa in mezzo.
      morphLeg(layover, FluidMotion.snappy(), initialVelocity)
      morphLeg(target, spec, initialVelocity = null)
    } else {
      morphLeg(target, spec, initialVelocity)
    }
  }

  private suspend fun morphLeg(target: FluidForm, spec: AnimationSpec<Float>?, initialVelocity: Float?) {
    // La sagoma del campo che si vede adesso, prima di fotografare la forma (F3.3): vedi [SlabTransit.startField].
    val shownField = shownSlabField()
    val origin = currentForm()
    val transit = buildTransit(origin, target)
    if (transit is SlabTransit && shownField != null) transit.inheritField(origin, shownField)
    // Il collo fra i pezzi non riparte da zero se questo viaggio ne interrompe un altro (4.0).
    transit.fusionStart = activeTransit?.let { it.fusionAt(it.progress.value) } ?: heldFusion
    // Il collo nel tempo (F1B.9) parte da quello che c'era: un viaggio interrotto o invertito non lo fa saltare.
    transit.neckStart = activeTransit?.let { it.neckAt(it.progress.value) } ?: heldNeck ?: -1f
    // L'installazione e il progresso zero DEVONO atterrare nello stesso snapshot, ed è per questo
    // che ogni transito possiede il proprio Animatable, nato a zero: con un progresso condiviso
    // il transito nuovo si mostrava per un fotogramma col valore vecchio (1), cioè con la sagoma
    // d'arrivo a taglia piena — il bordo del traguardo che lampeggia e sparisce.
    val previous = activeTransit
    // La velocità del viaggio interrotto si legge PRIMA di fermarlo: fermato, vale zero. È la
    // partenza viva del viaggio nuovo — senza, ogni cambio d'idea era uno scalino di velocità.
    val seed = initialVelocity
      ?: previous?.let {
        inheritedProgressVelocity(
          previousStart = it.startFrame,
          previousEnd = it.endFrame,
          velocity = it.progress.velocity,
          nextStart = transit.startFrame,
          nextEnd = transit.endFrame,
        )
      }
      ?: 0f
    // L'orologio del collo: il tempo che manca alla posa, letto dalla stessa molla che muove il progresso.
    transit.neckClock = PhysicsNeckClock.of(spec ?: FluidPhysicsJourney.Spring, seed)
    formState = target
    planeDrive = null
    activeTransit = transit
    heldFusion = 0f
    heldNeck = null
    transitPlanStamp = Float.NaN
    try {
      // Il viaggio sostituito è orfano: fermarlo libera la coroutine che lo stava animando, senza
      // aspettare la coda della sua molla.
      previous?.progress?.stop()
      if (reducedMotion) {
        transit.progress.snapTo(1f)
      } else {
        transit.progress.animateTo(1f, spec ?: FluidPhysicsJourney.Spring, initialVelocity = seed)
      }
    } finally {
      // Se un nuovo morphTo ci ha interrotti ha già sostituito il transito: il viaggio vivo è il
      // suo, e cancellarglielo qui lo farebbe scattare a fine corsa.
      if (activeTransit === transit) {
        // Cancellati a metà (3.0): la forma si ferma DOVE SI TROVA. Prima restava il traguardo in
        // `formState`, e togliere il transito faceva scattare la sagoma a fine corsa per un
        // fotogramma — il menu chiuso mentre si apriva saltava al pannello pieno e da lì tornava
        // indietro. Il morphTo successivo parte da questa forma, come un ritargeting vero.
        if (transit.progress.value != 1f) {
          formState = currentForm()
          heldFusion = transit.fusionAt(transit.progress.value)
          heldNeck = transit.neckAt(transit.progress.value)
        }
        activeTransit = null
        restPlanForm = null
      }
    }
  }

  /** Arriva senza viaggiare. */
  fun snapTo(target: FluidForm) {
    planeDrive = null
    activeTransit = null
    formState = target
    heldFusion = 0f
    heldNeck = null
    restPlanForm = null
  }

  /**
   * I raggi e il raccordo con cui il campo disegna adesso ogni pezzo di un viaggio fra lastre (cinque numeri per pezzo,
   * nell'ordine delle coppie del viaggio), o null se non c'è un viaggio fra lastre in corso o se il piano di adesso è
   * un'altra cosa (la goccia, una guida esterna). È ciò da cui deve ripartire un viaggio che lo interrompe.
   */
  private fun shownSlabField(): FloatArray? {
    if (externalDrive != null || planeDrive != null) return null
    val transit = activeTransit as? SlabTransit ?: return null
    val plan = ensurePlan()
    val count = transit.pairs.size
    if (plan.mode != PlanModeSlabs || plan.pieceCount != count) return null
    return FloatArray(count * SlabFieldStride).also { out ->
      for (i in 0 until count) {
        for (k in 0 until 4) out[i * SlabFieldStride + k] = plan.fieldRadii[i * 4 + k]
        out[i * SlabFieldStride + 4] = plan.fieldSmooth[i]
      }
    }
  }

  /**
   * La geometria di questo istante, come [FluidForm]: a riposo è [form], a metà volo è
   * l'interpolazione vera — il punto di partenza di un ritargeting.
   */
  private fun currentForm(): FluidForm {
    val transit = activeTransit ?: return formState
    return transit.formAt(transit.progress.value)
  }

  private fun buildTransit(origin: FluidForm, target: FluidForm): Transit {
    val capPx = MorphInflationCapDp * density
    val originSlabs = slabPiecesOf(origin)
    val targetSlabs = slabPiecesOf(target)
    val transit = if (originSlabs != null && targetSlabs != null) {
      val pairs = matchSlabPairs(originSlabs, targetSlabs)
      val blend = maxOf(groupBlendOf(origin), groupBlendOf(target))
      SlabTransit(pairs, blend, overshootCapPx = capPx)
    } else {
      // Invariante, non validazione: morphTo instrada le coppie gruppo↔sagoma dallo scalo PRIMA di
      // arrivare qui. Se questo scatta, il bug è a monte.
      check(origin !is FluidForm.Group && target !is FluidForm.Group) {
        "Transito gruppo↔sagoma senza scalo: morphTo doveva passare da physicsLayoverFor."
      }
      RingTransit(buildMatchedRings(origin, target), overshootCapPx = capPx)
    }
    transit.origin = origin
    return transit
  }

  /**
   * La lastra su cui la superficie riposa adesso, o null se viaggia o se non e' una lastra sola.
   * Fase di disegno: legge lo stato del viaggio, quindi chi la chiama si ridisegna quando cambia.
   *
   * Una guida esterna arrivata esattamente all'1 e' a riposo anche lei: il modale aperto e' una
   * lastra ferma, e la molla del chiamante, posata, vale 1 in punto.
   */
  internal fun restSlabNow(): FluidForm.Slab? = restFormNow() as? FluidForm.Slab

  /** Come [restSlabNow], per ogni famiglia: la forma ferma di adesso, o null in viaggio. */
  internal fun restFormNow(): FluidForm? {
    // Una forma arrivata che ancora oscilla non e' ferma: bordo e ombra la seguono come in viaggio.
    if (!liquid.quiet) return null
    val plane = planeDrive
    if (plane != null) return if (plane.progress() >= 1f) plane.frame().form() else null
    val drive = externalDrive
    if (drive != null) return if (drive.progress() == 1f) drive.to else null
    if (activeTransit != null) return null
    return formState
  }

  /** Da dove parte il viaggio in corso, o null a riposo (o per un transito costruito a mano). */
  internal fun journeyOriginNow(): FluidForm? {
    // Il piano flottante non ha un capo da cui parte: la sua forma è già quella di adesso.
    if (planeDrive != null) return null
    val drive = externalDrive
    if (drive != null) return drive.from
    return activeTransit?.origin
  }

  /**
   * La guida esterna, se c'è: i suoi due capi e il progresso di adesso (fase di disegno). Una guida non dice **dove va**
   * il gesto: un menu che si richiude è la stessa guida (tasto → pannello) col progresso che scende. Chi deve sapere la
   * meta effettiva (il velo dei grandi, giro di correzione unico di F1B) la legge dal verso del progresso.
   */
  internal fun externalDriveNow(): PhysicsDriveNow? {
    if (planeDrive != null) return null
    val drive = externalDrive ?: return null
    return PhysicsDriveNow(drive.from, drive.to, drive.progress())
  }

  /** Dove arriva il viaggio in corso, o null a riposo. */
  internal fun journeyTargetNow(): FluidForm? {
    if (planeDrive != null) return null
    val drive = externalDrive
    if (drive != null) return drive.to
    return if (activeTransit != null || !liquid.quiet) formState else null
  }

  /** Il piano di resa per il fotogramma corrente. Solo fase di disegno. */
  /**
   * La sagoma del clip per QUESTO fotogramma; vedi [Modifier.fluidPhysicsClip].
   *
   * Passa da [ensurePlan] come tutto il resto del disegno, cosi' il contenuto ritaglia esattamente
   * la silhouette che la superficie sta disegnando — non una ricostruita accanto, che divergerebbe
   * al primo cambio di geometria e nessuno saprebbe dire quale delle due ha ragione.
   */
  internal fun clipShapeNow(): Shape = ensurePlan().clipShape

  internal fun ensurePlan(): PhysicsRenderPlan {
    val plane = planeDrive
    if (plane != null) {
      val shape = plane.frame()
      if (planePlanShape !== shape) {
        planePlan.clipToBounds = maskInShader
        buildPlanePlan(planePlan, shape)
        planePlanShape = shape
      }
      return planePlan
    }
    val drive = externalDrive
    val liquidVersion = liquid.version
    if (drive != null) {
      val t = drive.progress()
      // La partenza viva si legge ADESSO, in fase di disegno: la cache vale per (t, rettangolo), non
      // per t soltanto, perche' la pressione che si scioglie muove la partenza anche a progresso fermo.
      // Dall'1 in su la partenza non entra nella geometria, e quindi nemmeno nella cache: a riposo
      // l'istanza resta una anche se il chiamante muove ancora il suo rettangolo.
      val startRect = if (t >= 1f) null else drive.startFrame?.invoke()
      val travel = drive.travelSlab?.invoke()
      val warp = if (travel != null) dropWarpNow() else null
      if (transitPlanTransit !== drive.transit || transitPlanStamp != t || transitPlanStart != startRect ||
        transitPlanTravel != travel || transitPlanWarp != warp || transitPlanLiquid != liquidVersion
      ) {
        transitPlan.clipToBounds = maskInShader
        val toSlab = drive.to as? FluidForm.Slab
        // La partenza di questo istante: la capsula sul rettangolo vivo, o il `from` fisso.
        val fromSlab = if (startRect != null && toSlab != null) {
          FluidForm.Slab(startRect, FluidCornerRadii.all(startRect.minDimension / 2f))
        } else {
          drive.from as? FluidForm.Slab
        }
        if (travel != null) {
          if (liquid.quiet) buildRestPlan(transitPlan, travel) else buildDropPlan(transitPlan, travel, warp)
        } else if (!liquid.quiet && toSlab != null && fromSlab != null && drive.transit.pairs.size == 1) {
          // La goccia: vedi [buildDropPlan].
          buildDropPlan(transitPlan, dropRestSlab(fromSlab, toSlab, t, drive.overshootInflationX, drive.overshootInflationY))
        } else if (t >= 1f && toSlab == null) {
          // Un gruppo guidato da fuori non ha oltrepasso: all'1 e oltre e' il traguardo fermo, con
          // lo stesso piano di riposo di una forma che non viaggia.
          buildRestPlan(transitPlan, drive.to)
        } else if (t >= 1f && toSlab != null) {
          // All'1 e oltre la partenza non c'entra piu'. In punto all'1 e' la lastra ferma, con il
          // raccordo continuo della casa (la stessa silhouette di `buildRestPlan`, non il cerchio
          // del viaggio). Oltre, l'oltrepasso e' un GONFIORE, non un proseguimento: il traguardo
          // scalato attorno al suo centro, un moto solo, su tutti i lati. Senza gonfiore chiesto
          // resta il traguardo: estrapolare il lerp oltre l'1 voleva dire stirare la sagoma lungo
          // il viaggio — fianchi in dentro e fondo in fuori — e questo non lo fa piu' nessuno.
          buildRestPlan(transitPlan, drive.inflatedTarget(toSlab, t))
        } else if (startRect != null && toSlab != null && fromSlab != null) {
          // Partenza viva: una capsula sul rettangolo di questo istante (il tasto, premuto o a
          // riposo, e' tondo per costruzione), verso il traguardo del chiamante.
          buildSlabPlan(transitPlan, listOf(fromSlab to toSlab), blendRadius = 0f, t = t)
        } else {
          buildTransitPlan(transitPlan, drive.transit, t)
        }
        transitPlanTransit = drive.transit
        transitPlanStamp = t
        transitPlanStart = startRect
        transitPlanTravel = travel
        transitPlanWarp = warp
        transitPlanLiquid = liquidVersion
      }
      return transitPlan
    }
    val transit = activeTransit
    if (transit == null) {
      val form = formState
      if (!liquid.quiet && form is FluidForm.Slab) {
        // Arrivata, ma la goccia non si e' ancora posata: la sagoma ferma disegnata dalla goccia, in
        // un piano a parte — quello di riposo resta l'istanza stabile di sempre per quando si posa.
        if (settlePlanForm != form || settlePlanLiquid != liquidVersion) {
          settlePlan.clipToBounds = maskInShader
          buildDropPlan(settlePlan, form)
          settlePlanForm = form
          settlePlanLiquid = liquidVersion
        }
        return settlePlan
      }
      if (restPlanForm != form) {
        restPlan.clipToBounds = maskInShader
        buildRestPlan(restPlan, form, heldNeck?.let(::physicsNeckEncode) ?: heldFusion)
        restPlanForm = form
      }
      return restPlan
    }
    val t = transit.progress.value
    if (transitPlanTransit !== transit || transitPlanStamp != t || transitPlanLiquid != liquidVersion) {
      transitPlan.clipToBounds = maskInShader
      val restSlab = if (!liquid.quiet) transitRestSlab(transit, t) else null
      if (restSlab != null) {
        buildDropPlan(transitPlan, restSlab)
      } else {
        buildTransitPlan(transitPlan, transit, t)
      }
      transitPlanTransit = transit
      transitPlanStamp = t
      transitPlanLiquid = liquidVersion
    }
    return transitPlan
  }

  // --- La goccia ---------------------------------------------------------------------------------

  /**
   * La sagoma a riposo di un transito di [morphTo] fra due lastre al progresso [t], o null se il
   * transito non e' fra due lastre (gruppi e sagome libere viaggiano senza goccia).
   */
  private fun transitRestSlab(transit: Transit, t: Float): FluidForm.Slab? {
    val single = (transit as? SlabTransit)?.pairs?.singleOrNull() ?: return null
    if (t > 1f) return transit.overshootPairs(t).first().second
    val v = lerpSlab(single.first, single.second, t)
    return FluidForm.Slab(Rect(v[0], v[1], v[0] + v[2], v[1] + v[3]), FluidCornerRadii(v[4], v[5], v[6], v[7]))
  }

  /**
   * Il piano della goccia: l'anello da disegnare ([buildDropRing], la sagoma a riposo [rest] piu' lo
   * scarto della goccia) nello shader poligonale, senza erosione e col path a segmenti — la maschera,
   * la pellicola e il bordo raccontano la stessa sagoma. Il contenuto si posa sulla sagoma a riposo
   * spostata dello scarto del baricentro, e si ritaglia sul rettangolo arrotondato che sta dentro la
   * goccia di adesso ([dropInnerClip]).
   */
  private fun buildDropPlan(plan: PhysicsRenderPlan, current: FluidForm.Slab, warp: FluidDropWarp? = null) {
    // La molla e l'integratore ricevono lo stesso vsync, ma possono svegliarsi in ordine diverso.
    // Gli scarti appartengono alla lastra campionata: applicarli alla prossima ripiega l'anello.
    val sampled = if (useSampledDropFrame) sampledDropSlab else null
    val rest = sampled ?: current
    // Anche il contorno della nascita (il menu dal gruppo) e' quello del passo: lastra e contorno vanno in coppia.
    val paired = if (sampled != null) sampledDropWarp else warp
    val reach = buildDropRing(rest, liquid, DropRenderPoints, dropRing, dropPerimeter, dropParameters, fieldCorners = liquidBody, warp = paired)
    plan.writeRing(dropRing, DropRenderPoints, smoothPath = false)
    plan.soften = 0f
    val dx = liquid.offsetX
    val dy = liquid.offsetY
    val f = rest.frame
    val moved = Rect(f.left + dx, f.top + dy, f.right + dx, f.bottom + dy)
    plan.contentBounds = moved
    // Il ritaglio e' il vetro di adesso, non la sagoma a riposo: dove la goccia e' rimasta indietro il
    // contenuto non sta sopra la pagina (vedi [dropInnerClip]).
    plan.contentClipRect = dropInnerClip(rest, dropRing, DropRenderPoints, dropPerimeter, dropParameters, dropPoint, paired)
    plan.dropRest = rest
    plan.dropWarp = paired
    plan.dropReach = reach
    plan.dropSettle = physicsDropSettle(liquid.lens, reach / density.coerceAtLeast(0.0001f))
  }

  /**
   * La sagoma a riposo che la goccia insegue in questo istante, o null se non ce n'e' una (un gruppo,
   * una sagoma libera): il lerp del viaggio fra due lastre, il traguardo gonfiato oltre l'1, la
   * partenza sgonfiata sotto lo 0, la forma ferma a riposo. Fase di disegno, o dal conducente.
   */
  internal fun dropRestSlabNow(): FluidForm.Slab? {
    // Il piano flottante non ha goccia: vedi [driveByFrame].
    if (planeDrive != null) return null
    val drive = externalDrive
    if (drive != null) {
      val to = drive.to as? FluidForm.Slab ?: return null
      if (drive.transit.pairs.size != 1) return null
      val t = drive.progress()
      drive.travelSlab?.invoke()?.let { return it }
      val startRect = if (t >= 1f) null else drive.startFrame?.invoke()
      val from = startRect?.let { FluidForm.Slab(it, FluidCornerRadii.all(it.minDimension / 2f)) }
        ?: drive.from as? FluidForm.Slab ?: return null
      return dropRestSlab(from, to, t, drive.overshootInflationX, drive.overshootInflationY)
    }
    val transit = activeTransit
    if (transit != null) return transitRestSlab(transit, transit.progress.value)
    return formState as? FluidForm.Slab
  }

  /**
   * La deformazione della goccia in questo istante ([FluidDropWarp], `driveExternally(travelWarp = …)`), o null: senza
   * guida esterna o senza contorno in viaggio, col movimento ridotto, o se non cambia niente. Fase di disegno, o dal
   * conducente.
   */
  internal fun dropWarpNow(): FluidDropWarp? {
    if (planeDrive != null || reducedMotion) return null
    val drive = externalDrive ?: return null
    val warp = drive.travelWarp?.invoke() ?: return null
    return if (warp.isIdentity && warp.lens <= 0f && warp.magnify <= 0f && warp.clarity <= 0f) null else warp
  }

  /**
   * Dove sta l'ancora della goccia in questo istante: il centro della partenza (il tasto, la riga, la
   * forma da cui il morph e' partito). La goccia che si posa dopo l'arrivo tiene l'ultima.
   */
  internal fun dropAnchorNow(): Offset {
    val drive = externalDrive
    if (drive != null) {
      val t = drive.progress()
      val start = (if (t >= 1f) null else drive.startFrame?.invoke()) ?: drive.from.frame
      return start.center.also { lastAnchor = it }
    }
    val transit = activeTransit
    if (transit != null) {
      return (transit.origin?.frame ?: transit.startFrame).center.also { lastAnchor = it }
    }
    return if (lastAnchor.isSpecified) lastAnchor else formState.frame.center
  }

  /**
   * La sagoma di ADESSO senza la goccia: quella che il moto descrive. Null per un gruppo: i pezzi
   * hanno gia' i loro ponti, e una goccia sola non c'e'.
   */
  internal fun bodyFrameNow(): Rect? = dropRestSlabNow()?.frame

  /**
   * Dove posare il contenuto in questo istante: la sagoma a riposo spostata dello scarto del
   * baricentro della goccia. Fase di disegno.
   */
  internal fun contentBoundsNow(): Rect = ensurePlan().contentBounds

  /**
   * La sagoma su cui ritagliare un contenuto posato su [frame] (nelle sue coordinate) mentre il vetro
   * si forma, si posa o si ritira: il rettangolo arrotondato a riposo di quell'istante, spostato con la
   * goccia (non il contorno libero: un rettangolo arrotondato prende il ritaglio hardware del nodo). Fase
   * di disegno.
   *
   * Null solo dove il corpo contiene di sicuro il contenuto: dall'arrivo in poi ([progress] all'1 o
   * oltre) **e** con la goccia posata. Lì il corpo è grande quanto il contenuto o di più (l'oltrepasso
   * è un gonfiore), e a riposo un ritaglio sarebbe lavoro per niente. Una goccia che si sta ancora
   * posando, invece, è più stretta della sagoma a riposo anche dopo l'arrivo: la coda arriva dopo la
   * molla, e la molla del menu resta sopra l'1 per ~200 ms (da ~150 a ~355 ms dell'apertura). Prima il
   * ritaglio si spegneva all'1 e in quella finestra le voci stavano sopra la pagina, fuori dal vetro (S9,
   * il contestuale a +243 ms: «Elimina», il cestino e i capi dei separatori sotto la goccia).
   */
  internal fun travelClipIn(frame: Rect, progress: Float): RoundRect? {
    val plan = ensurePlan()
    // Il ritaglio della goccia c'è finché la goccia non si è posata, qualunque sia il progresso.
    val drop = plan.contentClipRect
    if (progress >= 1f && drop == null) return null
    val round = drop ?: plan.silhouetteRoundRect ?: return null
    return round.translate(Offset(-frame.left, -frame.top))
  }

  /**
   * Il conducente della goccia: la superficie lo lancia e lui, a riposo, non chiede niente.
   *
   * Aspetta che la sagoma a riposo cambi (uno `snapshotFlow` sulla geometria di adesso: nessun
   * fotogramma chiesto finche' niente si muove, D9), poi alimenta la goccia fotogramma per fotogramma
   * finche' non si e' posata — anche dopo che il viaggio e' finito, perche' la coda arriva dopo la
   * molla — e torna ad aspettare. Uno per stato: due conducenti alimenterebbero lo stesso fotogramma
   * due volte, e la goccia scarta il secondo (stesso istante, niente passo).
   *
   * Il tempo della goccia e' quello delle animazioni di Compose: diviso per la scala di durata del
   * sistema ([MotionDurationScale], "Scala durata animatore"), come la molla del viaggio che la guida.
   * Al rallentatore la coda resta indietro quanto a velocita' piena, e a scala zero non c'e' goccia.
   */
  internal suspend fun runLiquid() {
    try {
      while (true) {
        snapshotFlow { dropRestSlabNow() }.drop(1).first()
        liquid.forgetHistory()
        // F8.8b: quando la goccia si sveglia e quando si posa, sotto `log.tag.FluidGlassFrame`. Una goccia sveglia e mai
        // posata e' una sagoma ondulata ferma a riposo (il popover della capsula sul Tab S9).
        GlassFrameDiagnostics.event("dropAwake", this) { "quiet=${liquid.quiet}" }
        var steps = 0
        var alive = true
        while (alive) {
          val scale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
          alive = withFrameNanos { now ->
            val slab = dropRestSlabNow()
            if (slab == null || !liquidAllowed || !(scale > 0f)) {
              liquid.reset()
              false
            } else {
              stepDrop(slab, if (scale == 1f) now else (now / scale.toDouble()).toLong())
            }
          }
          steps++
        }
        GlassFrameDiagnostics.event("dropQuiet", this) { "frames=$steps quiet=${liquid.quiet}" }
      }
    } finally {
      liquid.reset()
    }
  }

  internal fun stepDrop(slab: FluidForm.Slab, timeNanos: Long, density: Float = this.density): Boolean {
    // La deformazione della goccia (il contestuale, `FluidDropWarp`): la stessa per la molla e per l'anello.
    val warp = dropWarpNow()
    sampleRestContour(slab, liquid.points, dropRest, dropPerimeter, warp)
    val anchor = dropAnchorNow()
    if (useSampledDropFrame) {
      sampledDropSlab = slab
      sampledDropWarp = warp
    }
    return liquid.step(
      timeNanos = timeNanos,
      restContour = dropRest,
      anchorX = anchor.x,
      anchorY = anchor.y,
      reachPx = dropAnchorReachDp * density,
      density = density,
      edgeTension = dropEdgeTension,
      lensFloor = warp?.lens ?: 0f,
    )
  }
}

/** Ricorda uno stato fisico. La forma iniziale conta solo alla prima composizione. */
@Composable
fun rememberFluidPhysicsState(initial: FluidForm): FluidPhysicsState {
  val state = remember { FluidPhysicsState(initial) }
  state.reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  state.density = LocalDensity.current.density
  return state
}

/** I capi di una guida esterna e il suo progresso in un istante: vedi [FluidPhysicsState.externalDriveNow]. */
internal class PhysicsDriveNow(val from: FluidForm, val to: FluidForm, val progress: Float)

/** La guida del piano flottante: vedi [FluidPhysicsState.driveByFrame]. Le lambda si sostituiscono sul posto. */
private class PhysicsPlaneDrive(var frame: () -> PhysicsPlaneShape, var progress: () -> Float)

/**
 * Un campo del piano flottante in un fotogramma (F3.2c): ciò che il contenitore calcola e la superficie fisica disegna.
 * Immutabile: chi guida ne crea una nuova quando qualcosa cambia, e l'identità è la chiave della cache del piano.
 *
 * @param slabs le lastre, nelle coordinate del nodo della superficie (al più otto).
 * @param nominalBlend il raggio di fusione nominale del collo, in pixel di layout (la distanza di fusione del contenitore).
 * @param neck la coordinata del collo nel tempo (`PhysicsPlaneNeck`, -1..1) fra pezzi distinti: -1 a riposo.
 * @param unions per ogni lastra l'indice della sua unione, -1 se non ne ha: due lastre della stessa unione hanno sempre il
 *   collo pieno ([physicsPlaneNeckBlend]). Null: nessuna unione.
 * @param pinch l'esponente della vita del collo: [PhysicsPlaneNeckPinch] di serie.
 * @param materialize quanto c'è del campo (comparsa per lente), 0..1: lo applica `glassMaterializeRecipe`. Uno per i
 *   campi di vetri che viaggiano.
 * @param softMaterialize la comparsa morbida (F6B.10): contorno e lente insieme (`GlassMaterializeSoftEnd`).
 * @param motion il peso del vetro in viaggio, 0..1 ([physicsPlaneMotion]): zero da fermi.
 * @param resting vero per un riposo: la sagoma ha il raccordo continuo della casa, come una forma ferma.
 * @param journeyLight il livello della luce del **carattere del viaggio** (F3.9, `GlassLiquidJourney.light`), 0..1: zero
 *   fuori dal piano, da fermi e nei campi che compaiono o scompaiono per lente. Lo calcola
 *   `FluidGlassPlaneDriver.journeyLight` (che ricorda quello di un viaggio interrotto: invariante 16) e lo applica la
 *   ricetta delle superfici fisiche con `glassJourneyRecipe`, dopo il vetro in viaggio (`physicsLiquidRecipe`, F3.10).
 *   Il rigonfiamento dello stesso carattere è già nelle [slabs].
 * @param touch la **luce del dito** del campo (F3.10): il bagliore della pressione che ha fatto partire il viaggio,
 *   portato dentro il vetro toccato. Null senza tocco: il campo ha la luce del carattere e basta.
 * @param journeyGlow il livello dell'**alone** del carattere del viaggio (F3.12c, `GlassLiquidJourney.glowLevel`), 0..1:
 *   sale con la luce e si spegne prima (`FluidGlassPlaneDriver.journeyGlow`), e `glassJourneyRecipe` lo usa per l'alone
 *   al posto di [journeyLight]. Di serie [journeyLight]: l'alone segue la luce, com'era fino a F3.12b, al bit.
 */
internal class PhysicsPlaneShape(
  val slabs: List<FluidForm.Slab>,
  val nominalBlend: Float,
  val neck: Float = -1f,
  val unions: IntArray? = null,
  val pinch: Float = PhysicsPlaneNeckPinch,
  val materialize: Float = 1f,
  val motion: Float = 0f,
  val resting: Boolean = false,
  val journeyLight: Float = 0f,
  val touch: PhysicsPlaneTouch? = null,
  val journeyGlow: Float = journeyLight,
  /** Indice del donatore per un rilascio multiplo, l'indice del nato se il donatore è in un altro campo, -1 altrimenti. */
  val donorIndices: IntArray? = null,
  val slabFilms: List<GlassTint>? = null,
  /**
   * Il lato corto su cui la ricetta si sceglie, in pixel di layout; non finito (di serie) = quello delle lastre di
   * adesso (`physicsRecipeShortPx`). Lo dà il grappolo fermo del contenitore mentre un suo pezzo è premuto (F6-c): è il
   * lato corto **a riposo**, così la crescita del tasto premuto cambia la sua sagoma e non la ricetta di tutto il
   * campo. Prima un tondo da 44 dp premuto arrivava a ~59 dp, passava la soglia dei grandi (48 dp) e il velo di tutti
   * i pezzi si scuriva (S5d: il terzo tondo da 63,1 a 61,1 livelli mentre si teneva premuto il primo).
   */
  val recipeShortPx: Float = Float.NaN,
  val softMaterialize: Boolean = false,
) {
  /** La stessa geometria come [FluidForm]: una lastra da sola, un gruppo se sono di più. */
  fun form(): FluidForm = if (slabs.size == 1) slabs.single()
    else FluidForm.Group(slabs.take(FluidForm.Group.MaxPieces), nominalBlend)
}

/**
 * Il bagliore del dito di un campo del piano in un fotogramma (F3.10): lo stesso bagliore di un vetro fermo premuto
 * (`GlassLiquidGlowPeak` volte [amount], la campana attorno al dito allargata da `glowFill`), che il vetro toccato si
 * porta nel viaggio. Immutabile, come la forma che lo porta.
 *
 * @param amount 0..1, nelle unità della pressione: a fase zero è il bagliore che il vetro fermo mostrava.
 * @param x con [y], dove sta il dito, in pixel del nodo della superficie (come le lastre); non finito = il centro.
 * @param fill quanto il bagliore ha riempito la sagoma, 0..1: zero è il `glowFill` del vetro fermo, uno la sagoma intera.
 */
@Immutable
internal class PhysicsPlaneTouch(val amount: Float, val x: Float, val y: Float, val fill: Float)

/**
 * La guida esterna: un transito di lastre il cui orologio appartiene al chiamante. Un pezzo per
 * parte (il modale, la pillola che diventa player) o un gruppo per parte (la barra delle pagine).
 */
private class ExternalWindowDrive(
  val transit: SlabTransit,
  val from: FluidForm,
  val to: FluidForm,
  val progress: () -> Float,
  val overshootInflationX: Float,
  val overshootInflationY: Float,
  /** La partenza viva, se c'e': vedi `driveExternally(…, startFrame)`. Sostituibile sul posto. */
  var startFrame: (() -> Rect)? = null,
  /** Il contorno in viaggio fornito dal chiamante, condiviso fra il disegno e il conducente. */
  var travelSlab: (() -> FluidForm.Slab?)? = null,
  /** La deformazione di quel contorno ([FluidDropWarp]), condivisa allo stesso modo. */
  var travelWarp: (() -> FluidDropWarp?)? = null,
) {
  /** Il traguardo all'oltrepasso [t] > 1: gonfiato attorno al proprio centro (in punto all'1, lui). */
  fun inflatedTarget(to: FluidForm.Slab, t: Float): FluidForm.Slab {
    val over = t - 1f
    if (!(over > 0f)) return to
    val center = to.frame.center
    return inflateSlab(
      slab = to,
      centerX = center.x,
      centerY = center.y,
      scaleX = 1f + over * overshootInflationX,
      scaleY = 1f + over * overshootInflationY,
    )
  }
}

// La coreografia della casa (rincorsa e molla secca, il default fino alla 2.13) vive in
// FluidPhysicsJourney.kt come preset esplicito, `FluidPhysicsJourney.House`.

// --- Transiti ----------------------------------------------------------------
//
// Interni e non privati: i test del modulo esercitano buildTransitPlan direttamente, perché il
// pezzo di disciplina che protegge — "in viaggio ogni cambio di progresso è un'istantanea nuova,
// a riposo l'istanza è una sola" — è logica pura e va inchiodata senza un frame clock.

internal sealed class Transit(
  /**
   * Di quanto, in pixel, un lato puo' uscire dal traguardo all'oltrepasso: vedi
   * [morphOvershootScale]. Infinito per un transito costruito a mano (i test): resta il tetto in
   * frazione.
   */
  val overshootCapPx: Float,
) {
  /**
   * Il progresso è DEL transito, non dello stato: nasce a zero insieme a lui, così installazione
   * e partenza sono lo stesso fatto e nessun fotogramma può vedere il viaggio nuovo col valore
   * del vecchio.
   */
  val progress = Animatable(0f)

  /** La forma da cui il viaggio e' partito; null per un transito costruito a mano. */
  var origin: FluidForm? = null

  /** Il collo ereditato dal viaggio che questo interrompe (4.0): vedi [travelFusion]. Zero da fermi. */
  var fusionStart: Float = 0f

  /**
   * Per quanta parte del viaggio il collo nel progresso nasce e muore: quello di F1B, congelato
   * ([PhysicsProgressNeckFade]); lo usano le guide esterne e i transiti costruiti a mano.
   */
  val fusionFade: Float = PhysicsProgressNeckFade

  /** Quanto e' vivo il collo fra i pezzi a progresso [t]: vedi [travelFusion]. */
  fun fusionAt(t: Float): Float = travelFusion(t, fusionFade, fusionStart)

  /**
   * L'orologio del collo nel tempo (F1B.9): lo installa [FluidPhysicsState.morphTo] con la molla del viaggio. Null
   * per una guida esterna e per un transito costruito a mano, che restano sul collo nel progresso.
   */
  var neckClock: PhysicsNeckClock? = null

  /** La coordinata del collo ereditata dal viaggio che questo interrompe (F1B.9): -1 da fermi. */
  var neckStart: Float = -1f

  /** A che frazione del tempo del viaggio il collo si rompe: il profilo del materiale, letto alla nascita. */
  val neckBreak: Float = GlassLiquidProfile.Default.travel.neck

  /**
   * La coordinata del collo a progresso [t] (F1B.9, [physicsNeckAt]), col tempo letto dalla molla in quello stato; null
   * senza un orologio.
   */
  fun neckAt(t: Float): Float? = neckClock?.let { physicsNeckAt(it.elapsed(t, progress.velocity), neckStart, neckBreak) }

  /** Cio' che il piano porta in `fusion` a progresso [t]: il collo nel tempo se c'e' un orologio, se no il progresso. */
  fun planFusionAt(t: Float): Float = neckAt(t)?.let(::physicsNeckEncode) ?: fusionAt(t)

  /** Il riquadro della partenza e quello dell'arrivo: la direzione del viaggio, per l'eredita'. */
  abstract val startFrame: Rect
  abstract val endFrame: Rect
}

internal class SlabTransit(
  val pairs: List<Pair<FluidForm.Slab, FluidForm.Slab>>,
  val blendRadius: Float,
  overshootCapPx: Float = Float.POSITIVE_INFINITY,
) : Transit(overshootCapPx) {
  override val startFrame: Rect = unionOf(pairs) { it.first.frame }
  override val endFrame: Rect = unionOf(pairs) { it.second.frame }

  /**
   * La sagoma del campo da cui partono i pezzi di questo viaggio, se ne interrompe un altro (F3.3): per ogni lastra di
   * partenza (per identità) i quattro raggi e il raccordo con cui il campo la disegnava all'istante dell'interruzione.
   * Null per un viaggio che parte da fermo, dove la partenza è la regola forma -> piano della forma ferma.
   *
   * Perché: la forma fotografata a metà volo ([formAt]) è una [FluidForm.Slab] col raccordo continuo di serie, e la regola
   * forma -> piano le dà un raggio diverso da quello che il campo stava disegnando (una capsula a metà strada fra due
   * taglie non è più a mezzo lato, e un piccolo col raccordo prende il raggio equivalente, ×0,82): alla ridirezione il
   * raggio del campo scendeva di circa 5 px in un fotogramma, un salto d'area dello 0,10–0,13% (`passo-zero:area`, classe
   * RITARGETING di `MorphRobustnessKnownTest`). Fotografare col raccordo a zero lo chiudeva ma ne apriva altri 240 sulle
   * ridirezioni con la goccia (F1B.9): qui la forma resta quella di prima, e cambia solo da dove parte il campo.
   */
  var startField: java.util.IdentityHashMap<FluidForm.Slab, FloatArray>? = null
    private set

  /**
   * Prende [field] (cinque numeri per pezzo, [SlabFieldStride]) come sagoma del campo alla partenza delle lastre di
   * [origin], nell'ordine in cui [origin] le elenca.
   */
  fun inheritField(origin: FluidForm, field: FloatArray) {
    val slabs = when (origin) {
      is FluidForm.Slab -> listOf(origin)
      is FluidForm.Group -> origin.pieces.filterIsInstance<FluidForm.Slab>()
      is FluidForm.Poly -> return
    }
    if (slabs.size * SlabFieldStride != field.size) return
    val map = java.util.IdentityHashMap<FluidForm.Slab, FloatArray>()
    slabs.forEachIndexed { i, slab -> map[slab] = field.copyOfRange(i * SlabFieldStride, (i + 1) * SlabFieldStride) }
    startField = map
  }
}

/** Quanti numeri porta la sagoma del campo di un pezzo in [SlabTransit.startField]: quattro raggi e il raccordo. */
internal const val SlabFieldStride: Int = 5

internal class RingTransit(
  val rings: MatchedRings,
  overshootCapPx: Float = Float.POSITIVE_INFINITY,
) : Transit(overshootCapPx) {
  override val startFrame: Rect = ringBounds(rings.start, rings.count)
  override val endFrame: Rect = ringBounds(rings.end, rings.count)

  /** L'anello a progresso [t], in un array nuovo: il lerp fino all'1, oltre il traguardo gonfiato. */
  fun ringAt(t: Float): FloatArray = if (t > 1f) {
    overshootRing(t)
  } else {
    FloatArray(rings.count * 2).also { for (i in it.indices) it[i] = lerp(rings.start[i], rings.end[i], t) }
  }
}

private inline fun <T> unionOf(items: List<T>, frame: (T) -> Rect): Rect {
  if (items.isEmpty()) return Rect.Zero
  var union = frame(items[0])
  for (i in 1 until items.size) {
    val r = frame(items[i])
    union = Rect(
      minOf(union.left, r.left),
      minOf(union.top, r.top),
      maxOf(union.right, r.right),
      maxOf(union.bottom, r.bottom),
    )
  }
  return union
}

/**
 * Le coppie di un transito di Slab all'oltrepasso [t] > 1: ogni traguardo gonfiato attorno al centro
 * dell'ARRIVO intero (per un gruppo, il centro dell'unione: i pezzi si allontanano mentre crescono,
 * come un'immagine ingrandita), della scala di [morphOvershootScale]. Le coppie diventano
 * (traguardo gonfiato, traguardo gonfiato): a t = 1 il lerp le disegna esattamente.
 */
private fun SlabTransit.overshootPairs(t: Float): List<Pair<FluidForm.Slab, FluidForm.Slab>> {
  val scale = morphOvershootScale(t, endFrame, overshootCapPx)
  val center = endFrame.center
  return pairs.map { (_, to) ->
    val inflated = inflateSlab(to, center.x, center.y, scale, scale)
    inflated to inflated
  }
}

/** L'anello d'arrivo all'oltrepasso [t] > 1, gonfiato attorno al proprio centro. */
private fun RingTransit.overshootRing(t: Float): FloatArray {
  val scale = morphOvershootScale(t, endFrame, overshootCapPx)
  val center = endFrame.center
  return inflateRing(rings.end, rings.count, center.x, center.y, scale)
}

/**
 * Lo scalo di un viaggio che il renderer non sa fare in un colpo, o null se il viaggio è diretto.
 *
 * L'unica coppia servita è gruppo↔sagoma libera: il campo fuso parla rettangoli e l'anello parla
 * una sagoma sola, quindi in mezzo ci va un pannello — posato sul footprint della sagoma, così la
 * tappa dei pezzi è la fusione (o la scissione) e la tappa dell'anello è il cambio di pelle, e
 * nessuna delle due salta. Pura e testabile: è la funzione che garantisce che morphTo sia totale.
 */
internal fun physicsLayoverFor(origin: FluidForm, target: FluidForm): FluidForm.Slab? {
  val groupToPoly = origin is FluidForm.Group && target is FluidForm.Poly
  val polyToGroup = origin is FluidForm.Poly && target is FluidForm.Group
  if (!groupToPoly && !polyToGroup) return null
  // Raggio pieno: lo scalo è una GOCCIA, non un pannello — materiale che si raccoglie, non
  // un'altra forma. E la goccia sta sul footprint della DESTINAZIONE, sempre: così il viaggio è
  // "raccogliersi mentre si va, poi prendere la pelle nuova sul posto". Ancorata all'origine si
  // leggeva al contrario — la forma tornava indietro, poi ripartiva — ed è esattamente la
  // transizione strana che Alessio ha descritto sui viaggi verso i gruppi.
  return FluidForm.Slab(
    frame = target.frame,
    cornerRadii = FluidCornerRadii.all(target.frame.minDimension / 2f),
  )
}

/** I pezzi Slab di una forma, o null se la forma non è tutta di famiglia A. */
private fun slabPiecesOf(form: FluidForm): List<FluidForm.Slab>? = when (form) {
  is FluidForm.Slab -> listOf(form)
  is FluidForm.Group -> form.pieces.filterIsInstance<FluidForm.Slab>()
    .takeIf { it.size == form.pieces.size }
  is FluidForm.Poly -> null
}

private fun groupBlendOf(form: FluidForm): Float = (form as? FluidForm.Group)?.blendRadius ?: 0f

private fun matchSlabPairs(
  source: List<FluidForm.Slab>,
  target: List<FluidForm.Slab>,
): List<Pair<FluidForm.Slab, FluidForm.Slab>> {
  val pairs = matchPieces(
    sourceCenters = source.map { it.frame.center },
    sourceSizes = source.map { it.frame.maxDimension },
    targetCenters = target.map { it.frame.center },
    targetSizes = target.map { it.frame.maxDimension },
  )
  return pairs.map { (s, t) -> source[s] to target[t] }
}

// --- Il piano di resa ---------------------------------------------------------

internal const val PlanModeSlabs = 0
internal const val PlanModePoly = 1

/**
 * Quanto si erode il campo poligonale per sciogliere le faccette del campionamento, in px di
 * layout. Tre pixel, non uno e mezzo: su un cerchio grande la corda fra due campioni dell'anello
 * arriva a qualche pixel, e l'erosione deve coprirla o il bordo della rifrazione mostra i vertici.
 */
internal const val PolySoftenPx = 3f

/**
 * Tutto quello che serve a disegnare un fotogramma, in array riusati: il piano si riscrive, non si
 * rialloca. L'unica allocazione per cambio di progresso è l'istantanea di [shape], ed è voluta —
 * l'identità nuova è ciò che invalida la cache dell'outline.
 */
internal class PhysicsRenderPlan {
  var mode: Int = PlanModeSlabs
  var pieceCount: Int = 0
  val pieceRects = FloatArray(PhysicsMaxPieces * 4)
  val pieceRadii = FloatArray(PhysicsMaxPieces * 4)
  var blendRadius: Float = 0f

  /**
   * Il raggio di fusione di ogni pezzo con quelli prima di lui (3.0): un gruppo li ha tutti uguali a
   * [blendRadius]; il corpo liquido ne da' uno all'ancora che si svuota.
   */
  val pieceBlend = FloatArray(PhysicsMaxPieces)

  /**
   * Il raggio di fusione **chiesto** dal gruppo (F1B.2), prima che il varco lo spenga ([blendRadius] è quello
   * effettivo, che a compenetrazione va a zero): è la scala su cui normale e centro si pesano dove i pezzi si
   * sovrappongono (`physicsPieceSoft`). Zero per un anello e per una lastra sola.
   */
  var nominalBlend: Float = 0f
  var vertCount: Int = 0
  val verts = FloatArray(PhysicsMaxRingVertices * 2)
  var soften: Float = 0f
  val silhouette = Path()

  /**
   * Il path d'appoggio per l'unione: i pezzi entrano qui uno alla volta e si fondono nella
   * [silhouette] con `Path.op`. Sotto-path separati NON bastano: due pezzi che si sovrappongono
   * — cioè quasi tutto il viaggio di una fusione — farebbero tracciare al bordo speculare
   * entrambi i contorni, una doppia linea che attraversa il pannello.
   */
  val scratchPiece = Path()
  var clipToBounds: Boolean = true
  var shape: Shape = FluidPhysicsSilhouetteShape(silhouette, clipToBounds)

  /**
   * Il riquadro della sagoma. Col corpo liquido (3.0) e' quello del CORPO deformato, senza l'ancora
   * che si svuota: e' su di lui che si tarano la lente (taglia, centro della cupola), l'ombra e il
   * bordo — una goccia sola, col suo collo che non ha ne' ombra ne' bordo propri.
   */
  var bounds: Rect = Rect.Zero

  /**
   * Il riquadro su cui posare il contenuto: la sagoma a riposo spostata con la goccia (3.0). Fuori
   * dalla goccia e' [bounds].
   */
  var contentBounds: Rect = Rect.Zero

  /** Il ritaglio del contenuto mentre la goccia viaggia (3.0); null fuori dalla goccia. */
  var contentClipRect: RoundRect? = null

  /** La sagoma a riposo che la goccia insegue, se il piano e' una goccia: il bordo ne prende la luce. */
  var dropRest: FluidForm.Slab? = null

  /** La deformazione della goccia ([FluidDropWarp]), se c'e': la ricetta ne prende la lente. Null fuori dalla goccia. */
  var dropWarp: FluidDropWarp? = null

  // --- Cio' che legge solo il materiale nuovo (4.0, F1B.1): il vetro di prima non li guarda -----------------

  /**
   * Quanto e' vivo il collo fra i pezzi, 0..1 ([travelFusion]): zero a riposo, dove i pezzi sono separati del
   * tutto; in viaggio la campana del progresso. Il raggio di fusione del campo e' [pieceBlend] per questo.
   *
   * Nei viaggi di `morphTo` (F1B.9) la stessa casella porta invece il collo nel tempo, spostato in 2..4
   * ([physicsNeckEncode]): una casella sola, cosi' chi copia il piano porta il collo con se'.
   */
  var fusion: Float = 0f

  /**
   * Vero se il piano è del piano flottante ([buildPlanePlan], F3.2c): il raccordo di ogni pezzo è
   * [physicsPlaneNeckBlend], che conosce le unioni ([pieceUnion]) e l'esponente [neckPinch]. Falso per ogni altro piano.
   */
  var planeNeck: Boolean = false

  /** L'unione di ogni pezzo, -1 se non ne ha: vedi [planeNeck]. */
  val pieceUnion = IntArray(PhysicsMaxPieces) { -1 }
  var donorIndices: IntArray? = null

  /** L'esponente della vita del collo nel tempo di questo piano: [PhysicsNeckPinchExponent], uno solo per tutto l'engine (il collo «spesso», 1/2, scelta di Alessio del 2026-11-16). */
  var neckPinch: Float = PhysicsNeckPinchExponent

  /**
   * I raggi di ogni pezzo **come li vede il campo** (`glassSlabGeometry`, la regola forma -> piano di
   * `glassSurface`): a riposo quelli della lastra ferma (una capsula a mezzo lato, un piccolo al raggio
   * equivalente, un grande ai nominali); in viaggio il lerp fra quelli dei due capi, cosi' alla partenza e
   * all'arrivo la sagoma e' quella della lastra ferma e in mezzo non c'e' un fotogramma in cui scatta.
   */
  val fieldRadii = FloatArray(PhysicsMaxPieces * 4)

  /** Lo `smoothing` dell'angolo di ogni pezzo come lo vede il campo: vedi [fieldRadii]. */
  val fieldSmooth = FloatArray(PhysicsMaxPieces)

  /** Gli appoggi della regola forma -> piano: riusati, il piano non alloca per fotogramma. */
  val fieldFrom = GlassSlabGeometry()
  val fieldTo = GlassSlabGeometry()

  /**
   * Quanto l'anello della goccia si allontana al piu' da [dropRest], in pixel: fuori da questa fascia
   * attorno alla sagoma a riposo lo shader non ha lati da cercare. Senza goccia non conta.
   */
  var dropReach: Float = 0f

  /**
   * Quanto il campo della goccia e' gia' quello della lastra che la sostituira', 0..1 (F1B.2, [physicsDropSettle]):
   * zero in volo, uno quando si posa. Lo legge solo il materiale nuovo; senza goccia vale zero.
   */
  var dropSettle: Float = 0f

  /**
   * La sagoma corrente come rettangolo arrotondato, quando lo e' davvero: un solo pezzo di famiglia
   * Slab. Null per un gruppo o per una sagoma libera.
   *
   * Esiste per [Modifier.fluidPhysicsClip]: un `Outline.Rounded` prende il clip hardware del
   * RenderNode, un `Outline.Generic` fa mascherare l'intero nodo fuori schermo a ogni fotogramma —
   * la stessa distinzione, e la stessa scelta, di `FluidPhysicsSilhouetteShape.fastClipOutline`.
   */
  var silhouetteRoundRect: RoundRect? = null

  /**
   * La sagoma da dare a un clip di layer. Istanza nuova a ogni cambio di progresso — l'identita'
   * diversa e' cio' che invalida la cache dell'`Outline` esattamente quando deve, e a riposo
   * l'identita' stabile e' cio' che la fa riusare.
   */
  var clipShape: Shape = FluidPhysicsClipShape(silhouette, null)

  /**
   * Con più pezzi la tinta deve stare nello shader: il ponte dello smin non ha un path. E nello shader
   * sta anche quella della goccia (3.0): un path di quasi cento lati che cambia a ogni fotogramma si
   * rasterizza daccapo a ogni fotogramma, e lo shader la maschera gia'.
   */
  val tintInShader: Boolean get() = (mode == PlanModeSlabs && pieceCount > 1) || dropRest != null
}

private fun PhysicsRenderPlan.writeSlabPiece(index: Int, values: FloatArray) {
  val base = index * 4
  pieceRects[base] = values[0] + values[2] / 2f
  pieceRects[base + 1] = values[1] + values[3] / 2f
  pieceRects[base + 2] = values[2] / 2f
  pieceRects[base + 3] = values[3] / 2f
  pieceRadii[base] = values[4]
  pieceRadii[base + 1] = values[5]
  pieceRadii[base + 2] = values[6]
  pieceRadii[base + 3] = values[7]
}

/** La regola forma -> piano di una lastra ferma ([glassSlabGeometry]) per [slab], scritta in [out]. */
private fun fieldGeometryOf(slab: FluidForm.Slab, out: GlassSlabGeometry): GlassSlabGeometry {
  val f = slab.frame
  val r = slab.cornerRadii
  val s = if (slab.smoothing.isFinite()) slab.smoothing else 0f
  return glassSlabGeometry(
    width = f.width,
    height = f.height,
    topLeft = r.topLeft,
    topRight = r.topRight,
    bottomRight = r.bottomRight,
    bottomLeft = r.bottomLeft,
    smoothing = s,
    continuous = s > 0f,
    // Le forme stanno in pixel dello spazio della superficie, cioe' dello schermo.
    small = glassSlabIsSmall(f.maxDimension),
    out = out,
  )
}

/**
 * Scrive in [PhysicsRenderPlan.fieldRadii] e [PhysicsRenderPlan.fieldSmooth] il pezzo [index] come lo vede il
 * campo: il lerp a [t] fra la regola forma -> piano dei due capi, coi raggi tenuti dentro mezzo lato corto della
 * taglia di adesso ([values], gli otto numeri di [lerpSlab]).
 *
 * [shown], se c'è, prende il posto della regola forma -> piano della partenza: è la sagoma del campo che il viaggio
 * interrotto stava disegnando ([SlabTransit.startField]), cosi' alla ridirezione il campo riparte da lì.
 */
private fun PhysicsRenderPlan.writeFieldPiece(
  index: Int,
  from: FluidForm.Slab,
  to: FluidForm.Slab,
  t: Float,
  values: FloatArray,
  shown: FloatArray? = null,
) {
  val b = fieldGeometryOf(to, fieldTo)
  val half = minOf(values[2], values[3]) / 2f
  val f = t.coerceIn(0f, 1f)
  fun mix(x: Float, y: Float): Float = if (f <= 0f) x else if (f >= 1f) y else x + (y - x) * f
  val base = index * 4
  if (shown != null && shown.size >= SlabFieldStride) {
    fieldRadii[base] = mix(shown[0], b.topLeft).coerceIn(0f, half)
    fieldRadii[base + 1] = mix(shown[1], b.topRight).coerceIn(0f, half)
    fieldRadii[base + 2] = mix(shown[2], b.bottomRight).coerceIn(0f, half)
    fieldRadii[base + 3] = mix(shown[3], b.bottomLeft).coerceIn(0f, half)
    fieldSmooth[index] = mix(shown[4], b.smoothing)
    return
  }
  val a = if (to === from) b else fieldGeometryOf(from, fieldFrom)
  fieldRadii[base] = mix(a.topLeft, b.topLeft).coerceIn(0f, half)
  fieldRadii[base + 1] = mix(a.topRight, b.topRight).coerceIn(0f, half)
  fieldRadii[base + 2] = mix(a.bottomRight, b.bottomRight).coerceIn(0f, half)
  fieldRadii[base + 3] = mix(a.bottomLeft, b.bottomLeft).coerceIn(0f, half)
  fieldSmooth[index] = mix(a.smoothing, b.smoothing)
}

private fun PhysicsRenderPlan.finishSlabs(count: Int, blend: Float) {
  mode = PlanModeSlabs
  pieceCount = count
  planeNeck = false
  neckPinch = PhysicsNeckPinchExponent
  pieceUnion.fill(-1)
  nominalBlend = if (count > 1) blend.coerceAtLeast(0f) else 0f
  // Il ponte si spegne dove i pezzi si compenetrano: vedi effectiveBlendRadius — senza questo, la
  // fine di ogni fusione ha un alone di k/4 oltre il bordo, che a riposo sparisce di colpo.
  blendRadius = effectiveBlendRadius(blend, slabMinGap(pieceRects, count))
  pieceBlend.fill(blendRadius)
  contentClipRect = null
  dropRest = null
  dropWarp = null
  dropSettle = 0f
  soften = 0f
  vertCount = 0
  var union: Rect? = null
  for (i in 0 until count) {
    val base = i * 4
    val piece = Rect(
      pieceRects[base] - pieceRects[base + 2],
      pieceRects[base + 1] - pieceRects[base + 3],
      pieceRects[base] + pieceRects[base + 2],
      pieceRects[base + 1] + pieceRects[base + 3],
    )
    union = if (union == null) piece else Rect(
      minOf(union.left, piece.left),
      minOf(union.top, piece.top),
      maxOf(union.right, piece.right),
      maxOf(union.bottom, piece.bottom),
    )
  }
  bounds = union ?: Rect.Zero
  contentBounds = bounds
  shape = FluidPhysicsSilhouetteShape(silhouette, clipToBounds)
  silhouetteRoundRect = if (count == 1) {
    RoundRect(
      left = pieceRects[0] - pieceRects[2],
      top = pieceRects[1] - pieceRects[3],
      right = pieceRects[0] + pieceRects[2],
      bottom = pieceRects[1] + pieceRects[3],
      topLeftCornerRadius = CornerRadius(pieceRadii[0]),
      topRightCornerRadius = CornerRadius(pieceRadii[1]),
      bottomRightCornerRadius = CornerRadius(pieceRadii[2]),
      bottomLeftCornerRadius = CornerRadius(pieceRadii[3]),
    )
  } else {
    null
  }
  clipShape = FluidPhysicsClipShape(silhouette, silhouetteRoundRect)
}

private fun PhysicsRenderPlan.writeRing(ring: FloatArray, count: Int, smoothPath: Boolean = true) {
  mode = PlanModePoly
  pieceCount = 0
  nominalBlend = 0f
  planeNeck = false
  neckPinch = PhysicsNeckPinchExponent
  pieceUnion.fill(-1)
  contentClipRect = null
  dropRest = null
  dropWarp = null
  dropSettle = 0f
  vertCount = count
  ring.copyInto(verts, endIndex = count * 2)
  soften = PolySoftenPx
  silhouette.rewind()
  if (count > 2 && !smoothPath) {
    // La goccia (3.0): gia' fitta agli angoli, e il path deve essere ESATTAMENTE il poligono che lo
    // shader maschera, senza erosione — pellicola e maschera sullo stesso bordo.
    silhouette.moveTo(ring[0], ring[1])
    for (i in 1 until count) silhouette.lineTo(ring[i * 2], ring[i * 2 + 1])
    silhouette.close()
  } else if (count > 2) {
    // Quadratiche per i punti medi, non segmenti: la spezzata dell'anello sta dentro il pixel nel
    // campo di distanza (che la fonde con l'erosione), ma la tinta e il bordo speculare la
    // *tracciano* — e una corda di quattro pixel su un cerchio grande si vede come un vertice.
    // La curva passa per i punti medi dei lati usando i vertici come controlli: liscia ovunque,
    // dentro la corda per costruzione, e costa niente.
    fun x(i: Int) = ring[(i % count) * 2]
    fun y(i: Int) = ring[(i % count) * 2 + 1]
    silhouette.moveTo((x(0) + x(1)) / 2f, (y(0) + y(1)) / 2f)
    for (i in 1..count) {
      silhouette.quadraticTo(
        x(i), y(i),
        (x(i) + x(i + 1)) / 2f, (y(i) + y(i + 1)) / 2f,
      )
    }
    silhouette.close()
  }
  var left = Float.POSITIVE_INFINITY
  var top = Float.POSITIVE_INFINITY
  var right = Float.NEGATIVE_INFINITY
  var bottom = Float.NEGATIVE_INFINITY
  for (i in 0 until count) {
    left = minOf(left, ring[i * 2])
    top = minOf(top, ring[i * 2 + 1])
    right = maxOf(right, ring[i * 2])
    bottom = maxOf(bottom, ring[i * 2 + 1])
  }
  bounds = if (count > 0) Rect(left, top, right, bottom) else Rect.Zero
  contentBounds = bounds
  shape = FluidPhysicsSilhouetteShape(silhouette, clipToBounds)
  silhouetteRoundRect = null
  clipShape = FluidPhysicsClipShape(silhouette, null)
}

/**
 * La forma a progresso [t] di questo transito, come [FluidForm]: la geometria che [buildTransitPlan]
 * disegnerebbe, in tipi di forma invece che in array.
 *
 * Estratta da `currentForm()` (3.0) perche' serve anche fuori: e' il punto di partenza di un
 * ritargeting a meta' volo, e un test puo' confrontare il piano con il suo equivalente in forma
 * senza un frame clock. Per un transito di Slab e' l'interpolazione a otto numeri di [lerpSlab]
 * (un pezzo diventa [FluidForm.Slab], piu' pezzi un [FluidForm.Group] col raggio di fusione del
 * transito); per un anello e' una [FluidForm.Poly] sintetica sul lerp dei vertici. Oltre l'1 e' il
 * traguardo gonfiato (3.0), come lo disegna il piano.
 */
internal fun Transit.formAt(t: Float): FluidForm = when (this) {
  is SlabTransit -> {
    val source = if (t > 1f) overshootPairs(t) else pairs
    val at = if (t > 1f) 1f else t
    val slabs = source.map { (from, to) ->
      val v = lerpSlab(from, to, at)
      FluidForm.Slab(
        frame = Rect(v[0], v[1], v[0] + v[2], v[1] + v[3]),
        cornerRadii = FluidCornerRadii(v[4], v[5], v[6], v[7]),
      )
    }
    if (slabs.size == 1) slabs[0] else FluidForm.Group(slabs, blendRadius)
  }

  is RingTransit -> {
    val count = rings.count
    val ring = if (t > 1f) {
      overshootRing(t)
    } else {
      FloatArray(count * 2).also { for (i in it.indices) it[i] = lerp(rings.start[i], rings.end[i], t) }
    }
    polyFromRing(ring, count)
  }
}

/**
 * Il piano di un transito a progresso [t]. Fino all'1 e' il lerp; oltre, il traguardo gonfiato
 * uniformemente attorno al proprio centro ([morphOvershootScale]) — mai il lerp estrapolato, che
 * proseguiva il viaggio oltre l'arrivo e stirava la sagoma nella sua direzione.
 */
internal fun buildTransitPlan(plan: PhysicsRenderPlan, transit: Transit, t: Float) {
  when (transit) {
    // Il collo nel tempo (F1B.9) vive anche all'oltrepasso: la molla non e' posata, e il collo si rompe quando lo
    // dice il suo orologio. Il collo nel progresso li' e' spento, com'era.
    is SlabTransit -> if (t > 1f) {
      buildSlabPlan(plan, transit.overshootPairs(t), transit.blendRadius, 1f, fusion = transit.neckAt(t)?.let(::physicsNeckEncode) ?: 0f)
    } else {
      buildSlabPlan(
        plan, transit.pairs, transit.blendRadius, t, fusion = transit.planFusionAt(t), startField = transit.startField,
      )
    }

    is RingTransit -> {
      plan.writeRing(transit.ringAt(t), transit.rings.count)
      plan.fusion = 0f
    }
  }
}

/**
 * Il piano di una o piu' coppie di Slab a progresso [t]. Separato da [buildTransitPlan] perche' la
 * partenza viva di `driveExternally(…, startFrame)` costruisce la coppia a ogni cambio di
 * rettangolo, e un [SlabTransit] porta un `Animatable` che li' non serve.
 */
internal fun buildSlabPlan(
  plan: PhysicsRenderPlan,
  pairs: List<Pair<FluidForm.Slab, FluidForm.Slab>>,
  blendRadius: Float,
  t: Float,
  /**
   * Quanto e' vivo il collo (4.0): lo legge il solo materiale nuovo. Di serie la campana di [travelFusion]; un valore
   * fra 2 e 4 e' il collo nel tempo (F1B.9, [physicsNeckEncode]).
   */
  fusion: Float = travelFusion(t, PhysicsProgressNeckFade),
  /** La sagoma del campo da cui partono le lastre di un viaggio che ne interrompe un altro: [SlabTransit.startField]. */
  startField: Map<FluidForm.Slab, FloatArray>? = null,
) {
  // Una fusione all'oltrepasso non estrapola piu' (3.0): tutti i pezzi diventano il traguardo
  // gonfiato, che coincidono — un contorno solo, col ponte spento da effectiveBlendRadius. Il doppio
  // bordo di una volta lo impediscono l'unione geometrica e quel ponte spento, non un clamp.
  plan.silhouette.rewind()
  pairs.forEachIndexed { index, (from, to) ->
    val v = lerpSlab(from, to, t)
    plan.writeSlabPiece(index, v)
    plan.writeFieldPiece(index, from, to, t, v, startField?.get(from))
    // In viaggio la sagoma passa per angoli circolari: è l'eccezione documentata — un path
    // continuo ricalcolato a ogni fotogramma è esattamente quello che non ci si può permettere,
    // e in movimento le due curve stanno dentro il pixel.
    plan.scratchPiece.rewind()
    plan.scratchPiece.addRoundRect(
      RoundRect(
        rect = Rect(v[0], v[1], v[0] + v[2], v[1] + v[3]),
        topLeft = CornerRadius(v[4]),
        topRight = CornerRadius(v[5]),
        bottomRight = CornerRadius(v[6]),
        bottomLeft = CornerRadius(v[7]),
      ),
    )
    plan.mergePieceIntoSilhouette(index)
  }
  plan.finishSlabs(pairs.size, blendRadius)
  plan.fusion = if (pairs.size > 1) physicsNeckSanitize(fusion) else 0f
}

/** Le otto cifre di uno slab fermo: riquadro e raggi, questi tenuti fra zero e mezzo lato. */
internal fun slabValues(slab: FluidForm.Slab): FloatArray {
  val f = slab.frame
  val half = f.minDimension / 2f
  return floatArrayOf(
    f.left, f.top, f.width, f.height,
    slab.cornerRadii.topLeft.coerceIn(0f, half),
    slab.cornerRadii.topRight.coerceIn(0f, half),
    slab.cornerRadii.bottomRight.coerceIn(0f, half),
    slab.cornerRadii.bottomLeft.coerceIn(0f, half),
  )
}

/** Il lerp di due riquadri, lato per lato. */
internal fun lerpRect(from: Rect, to: Rect, t: Float): Rect = Rect(
  from.left + (to.left - from.left) * t,
  from.top + (to.top - from.top) * t,
  from.right + (to.right - from.right) * t,
  from.bottom + (to.bottom - from.bottom) * t,
)

/**
 * Il piano di una forma ferma.
 *
 * @param fusion quanto e' vivo il collo fra i pezzi di un gruppo per il materiale nuovo (4.0): zero, cioe' pezzi
 *   separati del tutto, salvo per una forma fermata a meta' viaggio (vedi `FluidPhysicsState`). Il vetro di
 *   prima non lo legge: il suo gruppo fermo resta fuso col raggio dichiarato, com'era.
 */
internal fun buildRestPlan(plan: PhysicsRenderPlan, form: FluidForm, fusion: Float = 0f) {
  when (form) {
    is FluidForm.Slab -> {
      plan.silhouette.rewind()
      writeRestSlab(plan, form, 0)
      plan.finishSlabs(1, 0f)
      plan.fusion = 0f
    }

    is FluidForm.Group -> {
      plan.silhouette.rewind()
      form.pieces.forEachIndexed { index, piece ->
        writeRestSlab(plan, piece as FluidForm.Slab, index)
      }
      plan.finishSlabs(form.pieces.size, form.blendRadius)
      plan.fusion = if (form.pieces.size > 1) physicsNeckSanitize(fusion) else 0f
    }

    is FluidForm.Poly -> {
      val ring = restRing(form)
      plan.writeRing(ring, ring.size / 2)
      plan.fusion = 0f
    }
  }
}

/**
 * Il piano di una forma del piano flottante ([PhysicsPlaneShape], F3.2c): le lastre come le dà il piano, i raggi come
 * li vede il campo (la regola forma -> piano di una lastra ferma, per ogni fotogramma), il collo nel tempo, le unioni e
 * l'esponente. A riposo la sagoma ha il raccordo continuo (come [buildRestPlan]); in viaggio angoli circolari, come ogni
 * viaggio di Fluid-physics. Un gruppo fermo senza unioni dà gli stessi uniform di [buildRestPlan] a fusione zero.
 */
internal fun buildPlanePlan(plan: PhysicsRenderPlan, shape: PhysicsPlaneShape) {
  val count = minOf(shape.slabs.size, PhysicsMaxPieces)
  plan.silhouette.rewind()
  for (index in 0 until count) {
    val slab = shape.slabs[index]
    if (shape.resting) {
      writeRestSlab(plan, slab, index)
    } else {
      val v = slabValues(slab)
      plan.writeSlabPiece(index, v)
      plan.writeFieldPiece(index, slab, slab, 1f, v)
      plan.scratchPiece.rewind()
      plan.scratchPiece.addRoundRect(
        RoundRect(
          rect = Rect(v[0], v[1], v[0] + v[2], v[1] + v[3]),
          topLeft = CornerRadius(v[4]),
          topRight = CornerRadius(v[5]),
          bottomRight = CornerRadius(v[6]),
          bottomLeft = CornerRadius(v[7]),
        ),
      )
      plan.mergePieceIntoSilhouette(index)
    }
  }
  plan.finishSlabs(count, shape.nominalBlend)
  plan.donorIndices = shape.donorIndices
  if (count > 1) {
    plan.fusion = physicsNeckEncode(shape.neck)
    plan.planeNeck = true
    plan.neckPinch = if (shape.pinch.isFinite() && shape.pinch > 0f) shape.pinch else PhysicsPlaneNeckPinch
    val unions = shape.unions
    if (unions != null) for (index in 0 until minOf(count, unions.size)) plan.pieceUnion[index] = unions[index]
  } else {
    plan.fusion = 0f
  }
}

private fun writeRestSlab(plan: PhysicsRenderPlan, slab: FluidForm.Slab, index: Int) {
  val f = slab.frame
  val half = f.minDimension / 2f
  val values = floatArrayOf(
    f.left, f.top, f.width, f.height,
    slab.cornerRadii.topLeft.coerceIn(0f, half),
    slab.cornerRadii.topRight.coerceIn(0f, half),
    slab.cornerRadii.bottomRight.coerceIn(0f, half),
    slab.cornerRadii.bottomLeft.coerceIn(0f, half),
  )
  plan.writeSlabPiece(index, values)
  plan.writeFieldPiece(index, slab, slab, 1f, values)
  // A riposo la silhouette è quella della casa: raccordo continuo, alla posizione vera del pezzo.
  plan.scratchPiece.rewind()
  plan.scratchPiece.addContinuousRoundRect(
    rect = f,
    topLeft = values[4],
    topRight = values[5],
    bottomRight = values[6],
    bottomLeft = values[7],
    smoothing = slab.smoothing,
  )
  plan.mergePieceIntoSilhouette(index)
}

/** Fonde [PhysicsRenderPlan.scratchPiece] nella silhouette: il primo pezzo entra diretto, gli altri per unione. */
private fun PhysicsRenderPlan.mergePieceIntoSilhouette(index: Int) {
  if (index == 0) {
    silhouette.addPath(scratchPiece)
  } else {
    silhouette.op(silhouette, scratchPiece, PathOperation.Union)
  }
}
