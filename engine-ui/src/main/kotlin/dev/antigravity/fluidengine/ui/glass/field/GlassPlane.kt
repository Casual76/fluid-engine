package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VectorizedFiniteAnimationSpec
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassContentCurve
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/** Identità e unioni sono già qualificate dal namespace dal chiamante; le coordinate sono in pixel. */
internal data class GlassPlanePiece(
  val key: Any,
  val bounds: Rect,
  val radii: FluidCornerRadii,
  val containerId: Any = Unit,
  val variant: GlassLiquidVariant = GlassLiquidVariant.Regular,
  val id: Any? = null,
  val unionId: Any? = null,
  val transition: GlassPlaneTransition = GlassPlaneTransition.MatchedGeometry,
  val role: Any? = null,
  val symbol: Any? = null,
) {
  init {
    require(bounds.planeValues().all { it.isFinite() } && bounds.width > 0f && bounds.height > 0f)
    // Come nel campo: una sottrazione fra coordinate frazionarie non deve rifiutare una capsula esatta.
    require(radii.planeValues().all { it.isFinite() && it >= 0f && it <= bounds.minDimension / 2f + 0.0001f })
  }
}

internal enum class GlassPlaneTransition { MatchedGeometry, Materialize, Identity }
internal enum class GlassPlaneChange { Matched, BornFromNeighbour, MergeIntoNeighbour, Materialize, Dematerialize, Identity }
internal enum class GlassPlaneMatch { Id, Role, Symbol, OutsideOrder }

/** Quattro è una preferenza di partizione; otto resta il limite del programma anche durante un viaggio. */
internal const val GlassPlanePreferredPieces = 4

/** Snapshot copiato dal registro: una modifica successiva del layout non riscrive la partenza del viaggio. */
internal class GlassPlaneState(
  pieces: List<GlassPlanePiece>,
  val spacing: Float,
  val bounds: Rect,
  val preferredPieces: Int = GlassPlanePreferredPieces,
) {
  val pieces: List<GlassPlanePiece> = pieces.toList()
  val clusters: List<GlassPlaneCluster>

  init {
    require(spacing.isFinite() && spacing >= 0f)
    require(bounds.planeValues().all { it.isFinite() } && bounds.width >= 0f && bounds.height >= 0f)
    require(preferredPieces in 1..GlassFieldMaxPieces)
    require(this.pieces.map { it.key }.distinct().size == this.pieces.size) { "chiavi di registrazione duplicate" }
    val identified = this.pieces.filter { it.id != null }.map { it.containerId to it.id }
    require(identified.distinct().size == identified.size) { "id duplicato nello stesso contenitore" }
    clusters = planeComponents(this.pieces) { a, b ->
      a.sameField(b) && (planeGap(a.bounds, b.bounds) <= spacing || a.sharesUnion(b))
    }.flatMap { component ->
      planePartition(component, { it.bounds }, preferredPieces, { a, b -> a.sharesUnion(b) })
    }.map { members ->
      GlassPlaneCluster(
        GlassPlaneClusterId(members.first().containerId, members.first().variant, members.map { it.key }.toSet()),
        members.toList(), planeBounds(members.map { it.bounds }),
      )
    }
  }

  fun clusterOf(key: Any): GlassPlaneCluster? = clusters.firstOrNull { group -> group.pieces.any { it.key == key } }
}

internal data class GlassPlaneClusterId(val containerId: Any, val variant: GlassLiquidVariant, val members: Set<Any>)
internal data class GlassPlaneCluster(val id: GlassPlaneClusterId, val pieces: List<GlassPlanePiece>, val bounds: Rect)

/** Il nodo conserva la chiave; aggiornare il riquadro non deve cambiare il suo posto nel registro. */
internal class GlassPlaneRegistry {
  private val pieces = LinkedHashMap<Any, GlassPlanePiece>()

  fun put(piece: GlassPlanePiece) { pieces[piece.key] = piece }
  fun remove(key: Any): Boolean = pieces.remove(key) != null
  fun snapshot(spacing: Float, bounds: Rect, preferredPieces: Int = GlassPlanePreferredPieces): GlassPlaneState =
    GlassPlaneState(pieces.values.toList(), spacing, bounds, preferredPieces)
}

internal data class GlassPlaneVelocity(
  /** Quattro velocità dei lati, in px/s: non è un riquadro geometrico. */
  val bounds: Rect = Rect.Zero,
  val radii: FluidCornerRadii = FluidCornerRadii.Zero,
  val materialize: Float = 0f,
)

internal data class GlassPlaneFlightId(val sourceKey: Any?, val targetKey: Any?, val change: GlassPlaneChange)

internal data class GlassPlanePieceFrame(
  val flightId: GlassPlaneFlightId,
  val piece: GlassPlanePiece,
  val bounds: Rect,
  val radii: FluidCornerRadii,
  val materialize: Float,
  val velocity: GlassPlaneVelocity,
  val change: GlassPlaneChange,
  val matchedBy: GlassPlaneMatch?,
  val bornFrom: Any?,
  val mergesInto: Any?,
  /** Il contenuto viene posato qui dal cliente, senza usare la taglia della lastra in volo (R2 §8.23). */
  val contentBounds: Rect,
  /**
   * La **fase** del volo (F3.8a): il tempo dalla partenza (o dalla ripartenza) diviso per il riposo a vista del volo, 0
   * alla partenza e 1 al riposo; per una comparsa o una scomparsa per lente il suo bilancio (430 e 470 ms), per chi
   * viaggia il riposo a vista del viaggio (`GlassPlaneJourney.settleMs`). È su questa che il contenuto del pezzo entra ed
   * esce (`glassPieceContentLook`). Uno per un volo senza tempo.
   */
  val phase: Float = 1f,
  /** Vero se il pezzo non cambia in questo viaggio: stesso vetro, stessa geometria, stessa unione, nessuna velocità. */
  val still: Boolean = false,
  /** L'unione del pezzo prima del cambio e dopo: diverse, l'unione nasce o si scioglie in questo viaggio (F3.8a). */
  val unionFrom: Any? = null,
  val unionTo: Any? = null,
  /**
   * Vero per i due voli di una **staffetta** (F3.11): un vetro che se ne va confluendo nel vetro che nasce da lui. È lo
   * stesso vetro che cambia identità: i due voli coincidono dal primo all'ultimo fotogramma, il contenuto del primo esce
   * e quello del secondo entra. [change] resta `MergeIntoNeighbour` per l'uno e `BornFromNeighbour` per l'altro.
   */
  val relay: Boolean = false,
  /**
   * Il **rigonfiamento in più** di questo pezzo (F3.12a), in unità del rigonfiamento del carattere del viaggio
   * (`GlassLiquidJourney.swell`): si somma al livello comune a tutto il campo. Zero per tutti, tranne chi è stato
   * rilasciato: in Apple il pezzo nato arriva al suo posto **gonfio** (+19,5% di altezza al colmo del superamento, quando
   * il donatore è già tornato alla sua misura) e si sgonfia tornando. Un viaggio che riparte lo porta con sé e lo spegne.
   */
  val swell: Float = 0f,
  /**
   * La fase su cui entra il **contenuto** del pezzo (F3.12a): [phase] per tutti, e più avanti per chi nasce da un vicino
   * in un viaggio in cui il primo rilascio viene presto. In Apple il contenuto nuovo compare 0,23 del viaggio **dopo il
   * primo rilascio** e non a una fase fissa: a 0,52 nel render «Select → Done» (rilascio a 0,29: è lì che F3.8a ha
   * tarato le fasi del contenuto), a 0,40 nelle clip a tempo vero, dove il donatore resta dov'è e rilascia a 0,17. È
   * questa che il contenitore dà a `glassPieceContentLook`.
   */
  val contentPhase: Float = phase,
)

/** Che cosa fa l'unione dentro un campo in un viaggio (F3.8a): niente, nasce (il collo cresce) o si scioglie (si rompe). */
internal enum class GlassPlaneUnionChange { None, Forming, Dissolving }

/** Il kernel e il palco appartengono al viaggio, non al conteggio del fotogramma corrente. */
internal data class GlassPlaneField(
  val flights: List<GlassPlaneFlightId>,
  val bounds: Rect,
  val kernel: GlassBodyKernel,
  val variant: GlassLiquidVariant,
  /**
   * Se nel campo un'unione nasce o si scioglie (F3.8a): «Unisci» e «Separa» sono un viaggio del collo anche quando
   * nessun pezzo si muove. Chi guida il campo porta il collo di quei pezzi nel tempo invece che pieno.
   */
  val union: GlassPlaneUnionChange = GlassPlaneUnionChange.None,
  /** Vero se nessun pezzo del campo cambia: il suo grappolo può restare al vetro di riposo (F3.8a). */
  val still: Boolean = false,
)

internal data class GlassPlaneFrame(
  val pieces: List<GlassPlanePieceFrame>,
  val fields: List<GlassPlaneField>,
  val finished: Boolean,
  /** Un riordino esplicito incompatibile con traiettorie senza incroci viene reso localmente per lente. */
  val crossingIds: Set<Any>,
)

internal data class GlassPlaneInterruption(val atMs: Double, val state: GlassPlaneState) {
  init { require(atMs.isFinite() && atMs >= 0.0) }
}

/**
 * I bersagli R4 B2 (alta) e B3 (media), dai token del piano in [FluidMotion] (F3.2). I numeri sono un profilo, non
 * curve parallele: si valuta la spec di FluidMotion, cambiando solo l'unità di tempo.
 *
 * [spec] è l'animazione di un'app (`FluidGlassAnimation.Custom`, F3.2): se c'è, sostituisce la molla di serie per ogni
 * canale e i bilanci non contano più (il tempo è quello della spec). Null: la molla [FluidMotion.standard] stirata nei
 * bilanci.
 */
internal data class GlassPlaneMotion(
  val contextMs: Double = FluidMotion.PlaneContextMillis.toDouble(),
  val appearMs: Double = FluidMotion.PlaneAppearMillis.toDouble(),
  val disappearMs: Double = FluidMotion.PlaneDisappearMillis.toDouble(),
  val initialContourScale: Float = FluidMotion.PlaneAppearScale,
  val spec: FiniteAnimationSpec<Float>? = null,
  /**
   * Se due pezzi **fermi** e vicini stanno nello stesso campo del viaggio (F3.8a). Falso di serie: due pezzi che non
   * cambiano restano separati come a riposo, e nessun collo nasce fra loro solo perché un terzo viaggia. Vero per la
   * distanza «larga» della tavola di F3.3, dove due pezzi vicini sono fusi anche a riposo.
   */
  val joinStill: Boolean = false,
  /**
   * Il bilancio di chi nasce da un vicino o vi confluisce (F3.11, F3.12a): la nascita o la confluenza **intera**, da
   * quando il cambio parte a quando il vetro rilasciato è tornato dal suo superamento. Su questo si contano l'attesa, lo
   * scaglionamento e la coda, e dentro ci sta la molla di rilascio. Di serie [FluidMotion.PlaneReleaseMillis] sul
   * bilancio del contesto, e lo segue se cambia.
   */
  val releaseMs: Double = contextMs * FluidMotion.PlaneReleaseMillis / FluidMotion.PlaneContextMillis,
) {
  init {
    require(listOf(contextMs, appearMs, disappearMs, releaseMs).all { it.isFinite() && it > 0.0 })
    require(initialContourScale.isFinite() && initialContourScale >= 1f)
  }

  /** La spec vettorizzata con cui viaggia ogni canale: la molla di serie, o quella dell'app. */
  internal val vectorized: VectorizedFiniteAnimationSpec<AnimationVector1D> =
    spec?.vectorize(Float.VectorConverter) ?: PlaneSpring
}

/**
 * Nessun clock Compose, cattura o disegno: un piano può essere preparato una volta e cercato in qualunque
 * ordine dal banco e dal contenitore. Ogni lato e raggio eredita la propria velocità, anche se la nuova
 * destinazione è ortogonale alla vecchia: proiettarla su un solo progresso perderebbe quell'informazione.
 *
 * **La coreografia della nascita e della confluenza (F3.11).** Fino a F3.8a ogni pezzo andava dritto al suo posto con un
 * orologio comune, e un tasto che ne faceva nascere altri cresceva simmetrico attorno al suo centro. In Apple no
 * (`artifacts/liquid-glass/sessioni/f3-11/misure.md`): il vetro di prima, il **donatore**, va per primo verso il suo
 * arrivo; chi nasce da lui gli resta dentro, lo segue, e dopo un ritardo ne è **rilasciato** con una molla che supera
 * l'arrivo e torna; più nati partono scaglionati, il più lontano per primo. Chi **confluisce** in un vicino fa il verso
 * opposto: gli entra dentro subito, dovunque quello stia andando. Qui:
 * - chi si sposta soltanto (`Matched`) viaggia com'era: è lui il donatore;
 * - un vetro che se ne va confluendo proprio nel vetro che nasce da lui è **lo stesso vetro che cambia identità** (la
 *   staffetta, [GlassPlanePieceFrame.relay]): i due volano come uno solo, da dov'era il primo a dove sta il secondo;
 * - gli altri nati e gli altri che confluiscono seguono la coreografia, coi numeri di [FluidMotion] (`PlaneRelease*`,
 *   `PlaneMerge*`, `PlaneDonor*`). Con una spec dell'app la coreografia non c'è: la spec porta la sua curva.
 *
 * **I tempi e il rimbalzo di Apple (F3.12a)**, dalle quattro clip a tempo vero dell'interfaccia
 * (`artifacts/liquid-glass/sessioni/f3-12a/misure.md`): una nascita intera dura mezzo secondo
 * ([GlassPlaneMotion.releaseMs]) e su quella durata contano geometria, contenuto, luce e collo ([settleMs] è la fine
 * della coda). In più:
 * - l'**attesa**: nei primi 0,17 del bilancio nessun vetro viaggia (si gonfia e si accende): il donatore che deve
 *   spostarsi parte dopo, e chi nasce è rilasciato quando il donatore ha già fatto un pezzo di strada;
 * - la molla di rilascio sta **fra il rilascio e la fine** (mezzo periodo 0,6 del tempo che resta: chi parte dopo è
 *   più svelto, e i colmi cadono insieme), e porta il vetro **rigido**: tutti i lati superano l'arrivo, e il contenuto
 *   sta al centro del vetro e rimbalza con lui;
 * - chi è rilasciato arriva **gonfio** ([GlassPlanePieceFrame.swell]): è il rigonfiamento che fa il rimbalzo più ampio;
 * - il **donatore rincula** verso chi esce e poi rimbalza oltre il suo posto, e chi accoglie un vetro che confluisce è
 *   **spinto** tutto intero e torna (`PlaneSway`).
 */
internal class GlassPlaneJourney private constructor(
  /** Da dove parte: lo stato di prima, o i vetri in volo all'istante dell'interruzione ([retarget]). */
  val from: GlassPlaneState,
  private val to: GlassPlaneState,
  private val motion: GlassPlaneMotion,
  seeds: Map<Any, GlassPlanePieceFrame>,
) {
  constructor(from: GlassPlaneState, to: GlassPlaneState, motion: GlassPlaneMotion = GlassPlaneMotion()) :
    this(from, to, motion, emptyMap())

  private val flights: List<PlaneFlight>
  val fields: List<GlassPlaneField>
  val crossingIds: Set<Any>
  val durationMs: Double

  /** Le chiavi d'arrivo dei pezzi che in questo viaggio non cambiano (F3.8a): vedi [GlassPlanePieceFrame.still]. */
  val stillKeys: Set<Any>

  /**
   * Quando i vetri che viaggiano (si spostano, nascono da un vicino, vi confluiscono) sono fermi **a vista** (F3.2c): il
   * bilancio del contesto ([GlassPlaneMotion.contextMs]) con la molla di serie, la fine del viaggio con una spec
   * dell'app. È la misura su cui il collo del piano conta il suo orologio (`physicsPlaneNeckMs`). Se i vetri che
   * viaggiano non si muovono, o non ce ne sono (sole comparse e scomparse, che non hanno collo), vale il riposo a vista
   * di tutti. Per chi è rilasciato o confluisce conta il suo bilancio ([GlassPlaneMotion.releaseMs]), che è la **fine
   * della coda** (F3.12a): a quell'istante è tornato dal superamento ed è al suo posto, al bit. Fino a F3.11 il riposo a
   * vista cadeva prima della fine (300 ms contro 345): il contenuto, la luce e il collo finivano prima della geometria,
   * e il collo si rompeva prima del colmo del superamento; ora stanno tutti sullo stesso orologio, e il collo si rompe
   * dopo il colmo, come in Apple.
   */
  val settleMs: Double

  /**
   * Dopo quanti millisecondi il donatore rilascia ogni vetro che nasce da lui (F3.11), per chiave d'arrivo: fino a lì il
   * vetro sta dentro il donatore in volo. Non c'è chi nasce in una staffetta, né chi nasce con una spec dell'app.
   */
  val releaseDelays: Map<Any, Double>

  init {
    val pairing = planePairs(from, to, seeds)
    crossingIds = pairing.crossingIds
    flights = pairing.pairs.map { pair -> PlaneFlight(pair, seeds, motion, to.bounds) }
    // Chi nasce da un vicino segue il volo di quel vicino finché non ne è rilasciato, e chi confluisce segue il volo di
    // chi lo accoglie (F3.11). Una catena che torna su se stessa non ha un capo da seguire: quei voli restano com'erano.
    for (flight in flights) {
      val host = when (flight.course) {
        PlaneCourse.Released -> flights.firstOrNull { it.pair.source === flight.pair.neighbour }
        PlaneCourse.Merging -> flights.firstOrNull { it.pair.target === flight.pair.neighbour }
        PlaneCourse.Standard -> continue
      }
      flight.follow(host)
    }
    for (flight in flights) {
      var host = flight.host
      var steps = 0
      while (host != null && host !== flight && steps <= flights.size) {
        host = host.host
        steps++
      }
      if (host != null) flight.follow(null)
    }
    // Un orologio comune per chi viaggia insieme: molle per lato con tempi diversi farebbero sorpassare
    // due pezzi che l'accoppiamento aveva ordinato correttamente.
    val contexts = flights.filter { it.pair.change in PlaneTravellingChanges }
    // Una spec dell'app porta il suo tempo: i bilanci sono quelli della molla di serie.
    if (contexts.isNotEmpty() && motion.spec == null) {
      val scale = planeTimeScale(contexts, motion.contextMs)
      contexts.forEach { it.timeScale = scale }
    }
    if (motion.spec == null) {
      flights.filter { it !in contexts }.forEach { flight ->
        val budget = if (flight.pair.change == GlassPlaneChange.Dematerialize) motion.disappearMs else motion.appearMs
        flight.timeScale = planeTimeScale(listOf(flight), budget)
      }
    }
    // L'attesa e lo scaglionamento (F3.11, F3.12a). Per l'attesa ([FluidMotion.PlaneReleaseDelay]) nessun vetro
    // viaggia; poi il donatore parte, e chi nasce è rilasciato quando il donatore ha fatto un pezzo di strada
    // ([FluidMotion.PlaneReleaseLead]: subito, se il donatore non va da nessuna parte). Dei nati dallo stesso donatore
    // che vanno dalla stessa parte, il più lontano dal suo arrivo parte per primo (R4 B2 (d): il tondo a 0,29 della
    // fase, il triangolo a 0,38). Poi ogni volo si prepara sul suo capo, che a questo punto ha il suo tempo.
    val waitMs = motion.releaseMs * FluidMotion.PlaneReleaseDelay
    val released = flights.filter { it.course == PlaneCourse.Released }
    released.groupBy { it.host to it.releaseSide() }.values.forEach { born ->
      born.sortedByDescending { it.releaseReach() }.forEachIndexed { rank, flight ->
        val lead = FluidMotion.PlaneReleaseLead * (flight.host?.travelShare() ?: 0f)
        val delay = FluidMotion.PlaneReleaseDelay + lead + rank * FluidMotion.PlaneReleaseStagger
        flight.delayMs = motion.releaseMs * min(delay, PlaneReleaseDelayCap)
      }
    }
    // Il contenuto di chi nasce entra contando dal primo rilascio (vedi [GlassPlanePieceFrame.contentPhase]): di quanto
    // il primo rilascio di questo viaggio viene prima di quello di un donatore che si sposta, su cui le fasi sono tarate.
    val contentLeadMs = if (released.isEmpty()) 0.0 else max(0.0,
      motion.releaseMs * (FluidMotion.PlaneReleaseDelay + FluidMotion.PlaneReleaseLead) - released.minOf { it.delayMs })
    // Il donatore e chi accoglie (F3.12a): aspettano, rinculano, sono spinti. In una staffetta i due voli gemelli sono
    // lo stesso vetro: hanno la stessa attesa e lo stesso dondolio, e restano uguali dal primo all'ultimo fotogramma.
    fun twinOf(flight: PlaneFlight): PlaneFlight? = if (!flight.pair.relay) null else flights.firstOrNull { other ->
      other !== flight && other.pair.relay &&
        (other.pair.target === flight.pair.neighbour && other.pair.neighbour === flight.pair.source ||
          other.pair.source === flight.pair.neighbour && other.pair.neighbour === flight.pair.target)
    }
    val guests = flights.filter { it.course != PlaneCourse.Standard && it.host != null }.groupBy { it.host!! }
    for ((host, around) in guests) {
      if (!host.sways) continue
      val twin = twinOf(host)
      if (around.any { it.course == PlaneCourse.Released } && host.waits && twin?.waits != false) {
        host.waitMs = waitMs
        twin?.waitMs = waitMs
      }
      val sway = planeSwayOf(host, around, motion, waitMs, to.bounds) ?: continue
      host.sway = if (host.sway == null) sway else host.sway!! + sway
      twin?.let { it.sway = if (it.sway == null) sway else it.sway!! + sway }
    }
    flights.forEach { it.prepare() }
    releaseDelays = flights.filter { it.course == PlaneCourse.Released }.associate { it.pair.target!!.key to it.delayMs }
    var duration = flights.maxOfOrNull { it.durationMs() } ?: 0.0
    var settle = if (motion.spec != null) {
      duration
    } else {
      fun restMs(of: List<PlaneFlight>) = of.maxOfOrNull { it.restMs() } ?: 0.0
      // Se i vetri che viaggiano non si muovono (un tasto fermo accanto a uno che compare), il riposo di tutti.
      restMs(contexts).takeIf { it > 0.0 } ?: restMs(flights)
    }
    // Un'unione che nasce o si scioglie è un viaggio anche se nessun pezzo si muove (F3.8a): dura quanto un cambio di
    // contesto, il tempo in cui il collo cresce o si strozza e si rompe.
    if (flights.any { it.unionChanged }) {
      settle = max(settle, motion.contextMs)
      duration = max(duration, settle)
    }
    durationMs = duration
    settleMs = settle
    // La fase di ogni volo: chi compare o scompare per lente conta sul suo bilancio, chi viaggia sul riposo a vista.
    flights.forEach { it.setPhaseClock(settle, contentLeadMs) }
    stillKeys = flights.filter { it.still }.mapNotNullTo(HashSet()) { it.pair.target?.key }
    val spacing = max(from.spacing, to.spacing)
    // Chi compare o scompare per lente è un campo a sé (F3.2c): lo shader ha un `materialize` per campo, e un vetro che
    // compare accanto a uno che si sposta, nello stesso campo, non poteva avere la sua comparsa (F3.2 la approssimava col
    // contorno che si stringe e una soglia del 5%). In un campo suo l'ha esatta: contorno, poi lente, poi simbolo. Non si
    // fonde col vicino mentre compare, e non deve: un vetro che si accende accanto a un altro non gli tira un collo.
    //
    // Due pezzi **fermi** non si uniscono in un campo solo perché sono vicini (F3.8a): a riposo sono separati, e in un
    // campo comune il collo dell'orologio nasceva pieno fra loro al primo fotogramma del palco e se ne andava a metà
    // viaggio («cerca» e «preferiti» mentre «play» compare, S3). Un pezzo fermo entra nel campo di uno che cambia, e due
    // della stessa unione (prima o dopo il cambio) stanno sempre insieme.
    fields = planeComponents(flights) { a, b ->
      a.pair.piece.sameField(b.pair.piece) && !a.lens && !b.lens &&
        (a.unitedWith(b) || planeFlightsMeet(a, b, spacing, motion.joinStill))
    }.flatMap { component ->
      planePartition(component, { it.sweptBounds() }, max(from.preferredPieces, to.preferredPieces),
        { a, b -> a.unitedWith(b) })
    }.map { group ->
      // Solo fra vetri che c'erano e ci sono: chi nasce in un'unione o vi confluisce ha il collo pieno, come prima.
      val kept = group.filter { it.pair.source != null && it.pair.target != null }
      val forming = kept.any { a -> kept.any { b -> a !== b && a.unitedAfter(b) && !a.unitedBefore(b) } }
      val dissolving = kept.any { a -> kept.any { b -> a !== b && a.unitedBefore(b) && !a.unitedAfter(b) } }
      GlassPlaneField(
        group.map { it.id }, planeBounds(group.map { it.sweptBounds() }),
        if (group.size == 1) GlassBodyKernel.One else GlassBodyKernel.Eight,
        group.first().pair.piece.variant,
        union = when {
          forming -> GlassPlaneUnionChange.Forming
          dissolving -> GlassPlaneUnionChange.Dissolving
          else -> GlassPlaneUnionChange.None
        },
        still = group.all { it.still },
      )
    }
  }

  fun frame(tMs: Double): GlassPlaneFrame {
    require(tMs.isFinite())
    val elapsed = max(0.0, tMs)
    return GlassPlaneFrame(flights.map { it.frame(elapsed) }, fields, elapsed >= durationMs, crossingIds)
  }

  fun retarget(atMs: Double, state: GlassPlaneState): GlassPlaneJourney {
    val current = frame(atMs)
    val live = current.pieces.filter { it.materialize > 0f || it.change != GlassPlaneChange.Dematerialize }
    val snapshot = GlassPlaneState(
      live.map { it.piece.copy(bounds = it.bounds, radii = it.radii) },
      to.spacing, to.bounds, to.preferredPieces,
    )
    return GlassPlaneJourney(snapshot, state, motion, live.associateBy { it.piece.key })
  }
}

internal fun planeJourneyFrame(
  tMs: Double,
  stateA: GlassPlaneState,
  stateB: GlassPlaneState,
  interruption: GlassPlaneInterruption? = null,
): GlassPlaneFrame {
  val journey = GlassPlaneJourney(stateA, stateB)
  return if (interruption == null || tMs < interruption.atMs) journey.frame(tMs)
  else journey.retarget(interruption.atMs, interruption.state).frame(tMs - interruption.atMs)
}

private val PlaneTravellingChanges = setOf(
  GlassPlaneChange.Matched, GlassPlaneChange.BornFromNeighbour, GlassPlaneChange.MergeIntoNeighbour,
)
private val PlaneSpring = FluidMotion.standard<Float>().vectorize(Float.VectorConverter)

private data class PlanePair(
  val source: GlassPlanePiece?,
  val target: GlassPlanePiece?,
  val change: GlassPlaneChange,
  val matchedBy: GlassPlaneMatch? = null,
  val neighbour: GlassPlanePiece? = null,
  val ghost: Boolean = false,
  /** La staffetta (F3.11): chi confluisce nel vetro che nasce da lui, e quel vetro. Vedi [GlassPlanePieceFrame.relay]. */
  val relay: Boolean = false,
) {
  val piece get() = target ?: source!!
}
private data class PlanePairing(val pairs: List<PlanePair>, val crossingIds: Set<Any>)

private fun planePairs(from: GlassPlaneState, to: GlassPlaneState, seeds: Map<Any, GlassPlanePieceFrame>): PlanePairing {
  val sources = from.pieces.toMutableList()
  val targets = to.pieces.toMutableList()
  val matched = ArrayList<PlanePair>()
  fun sameDomain(a: GlassPlanePiece, b: GlassPlanePiece) = a.sameField(b)
  fun accept(a: GlassPlanePiece, b: GlassPlanePiece, by: GlassPlaneMatch) {
    val change = when (b.transition) {
      GlassPlaneTransition.Identity -> GlassPlaneChange.Identity
      GlassPlaneTransition.Materialize -> if (a.key in seeds) GlassPlaneChange.Matched else GlassPlaneChange.Materialize
      GlassPlaneTransition.MatchedGeometry -> GlassPlaneChange.Matched
    }
    if (change == GlassPlaneChange.Materialize) return
    matched += PlanePair(a, b, change, by)
    sources.remove(a)
    targets.remove(b)
  }
  // Gli id sono un contratto, prima di qualsiasi euristica. Un id nuovo non consuma un altro id.
  targets.toList().filter { it.id != null }.forEach { b ->
    sources.firstOrNull { it.id == b.id && sameDomain(it, b) }?.let { accept(it, b, GlassPlaneMatch.Id) }
  }
  targets.toList().filter { it.id == null }.forEach { b ->
    sources.firstOrNull { it.key == b.key && it.key in seeds && sameDomain(it, b) }
      ?.let { accept(it, b, GlassPlaneMatch.OutsideOrder) }
  }
  val crossing = matched.filter { a -> matched.any { b -> a !== b && planeCrosses(a, b) } }
  val crossingIds = crossing.mapNotNull { it.piece.id }.toSet()
  crossing.forEach { pair ->
    matched.remove(pair)
    sources += pair.source!!
    targets += pair.target!!
  }
  fun side(piece: GlassPlanePiece, state: GlassPlaneState): Boolean = piece.bounds.center.x < state.bounds.center.x
  fun outside(piece: GlassPlanePiece, state: GlassPlaneState): Float =
    if (side(piece, state)) piece.bounds.center.x - state.bounds.left else state.bounds.right - piece.bounds.center.x
  for (by in listOf(GlassPlaneMatch.Role, GlassPlaneMatch.Symbol, GlassPlaneMatch.OutsideOrder)) {
    val anonymousTargets = targets.filter { it.id == null }.sortedBy { outside(it, to) }
    for (b in anonymousTargets) {
      if (b.transition != GlassPlaneTransition.MatchedGeometry) continue
      val a = sources.filter { it.id == null && sameDomain(it, b) && side(it, from) == side(b, to) }
        .sortedBy { outside(it, from) }.firstOrNull { candidate ->
          val agrees = when (by) {
            GlassPlaneMatch.Role -> b.role != null && b.role == candidate.role
            GlassPlaneMatch.Symbol -> b.symbol != null && b.symbol == candidate.symbol
            else -> true
          }
          agrees && matched.none { planeCrosses(PlanePair(candidate, b, GlassPlaneChange.Matched), it) }
        }
      if (a != null) accept(a, b, by)
    }
  }
  val pairs = ArrayList(matched)
  for (b in targets) {
    val forced = b.transition != GlassPlaneTransition.MatchedGeometry || b.id in crossingIds
    val neighbour = if (forced) null else from.pieces.filter { sameDomain(it, b) }
      .filter { planeGap(it.bounds, b.bounds) <= to.spacing }
      .sortedWith(compareBy({ planeGap(it.bounds, b.bounds) }, { planeKinship(it.bounds, b.bounds) }))
      .firstOrNull { donor -> pairs.none { planeCrosses(PlanePair(donor, b, GlassPlaneChange.BornFromNeighbour), it) } }
    pairs += PlanePair(null, b, when {
      b.transition == GlassPlaneTransition.Identity -> GlassPlaneChange.Identity
      neighbour != null -> GlassPlaneChange.BornFromNeighbour
      else -> GlassPlaneChange.Materialize
    }, neighbour = neighbour)
  }
  for (a in sources) {
    val forced = a.transition != GlassPlaneTransition.MatchedGeometry || a.id in crossingIds ||
      to.pieces.any { it.id != null && it.id == a.id && sameDomain(a, it) }
    val neighbour = if (forced) null else to.pieces.filter { sameDomain(a, it) }
      .filter { planeGap(a.bounds, it.bounds) <= to.spacing }
      .sortedBy { planeKinship(a.bounds, it.bounds) }
      .firstOrNull { neighbour -> pairs.none { planeCrosses(PlanePair(a, neighbour, GlassPlaneChange.MergeIntoNeighbour), it) } }
    pairs += PlanePair(a, null, when {
      a.transition == GlassPlaneTransition.Identity -> GlassPlaneChange.Identity
      neighbour != null -> GlassPlaneChange.MergeIntoNeighbour
      else -> GlassPlaneChange.Dematerialize
    }, neighbour = neighbour, ghost = a.id in crossingIds || to.pieces.any {
      it.key == a.key || a.id != null && it.id == a.id && sameDomain(a, it)
    })
  }
  // La staffetta (F3.11): chi confluisce proprio nel vetro che nasce da lui è lo stesso vetro che cambia identità.
  val born = pairs.filter { it.change == GlassPlaneChange.BornFromNeighbour }
  val merging = pairs.filter { it.change == GlassPlaneChange.MergeIntoNeighbour }
  fun relays(pair: PlanePair): Boolean = when (pair.change) {
    GlassPlaneChange.BornFromNeighbour -> merging.any { it.source === pair.neighbour && it.neighbour === pair.target }
    GlassPlaneChange.MergeIntoNeighbour -> born.any { it.target === pair.neighbour && it.neighbour === pair.source }
    else -> false
  }
  return PlanePairing(pairs.map { if (relays(it)) it.copy(relay = true) else it }, crossingIds)
}

/**
 * Quanto è lontano un vetro dall'essere **lo stesso** di un altro (F3.11): la distanza fra i centri più, contata il
 * doppio ([PlaneKinshipSize]), la differenza di misura. Sceglie il vicino in cui un vetro confluisce e, a parità di
 * varco, quello da cui nasce. A misure uguali è la distanza fra i centri, com'era da F3.1. Fra «Seleziona», due tondi e
 * «Fatto» è «Fatto» a continuare «Seleziona» (l'elemento che non cambia di misura, osservazione di Alessio del
 * 2026-11-16 su R4 B2 (d)), anche se il centro del tondo di mezzo è più vicino.
 */
private fun planeKinship(a: Rect, b: Rect): Float =
  planeCenterDistance(a, b) + PlaneKinshipSize * (abs(a.width - b.width) + abs(a.height - b.height))

/** Quanto pesa la differenza di misura rispetto alla distanza in [planeKinship]: scelta, non misura. */
private const val PlaneKinshipSize = 2f

/**
 * Come viaggia la geometria di un volo (F3.11). [Standard]: la molla del piano da dove parte a dove arriva, com'era da
 * F3.1 (chi si sposta, chi compare o scompare, la staffetta, e tutti con una spec dell'app). [Released]: chi nasce da un
 * vicino sta dentro il vicino in volo finché non ne è rilasciato, poi ha la sua molla. [Merging]: chi confluisce in un
 * vicino ha una molla sullo scarto dal vicino in volo.
 */
private enum class PlaneCourse { Standard, Released, Merging }

private class PlaneFlight(
  val pair: PlanePair,
  seeds: Map<Any, GlassPlanePieceFrame>,
  private val motion: GlassPlaneMotion,
  /** Il riquadro del contenitore: il superamento di un rilascio non ne esce (vedi [prepare]). */
  private val limits: Rect,
) {
  val id = GlassPlaneFlightId(pair.source?.key, pair.target?.key, pair.change)
  private val spring = motion.vectorized

  /** Vero per chi compare o scompare per lente: un campo a sé (vedi `GlassPlaneJourney.fields`). */
  val lens: Boolean get() = pair.change == GlassPlaneChange.Materialize || pair.change == GlassPlaneChange.Dematerialize
  private val seed = seeds[pair.source?.key ?: pair.neighbour?.key]
  private val origin = pair.source ?: pair.neighbour ?: pair.target!!
  private val destination = pair.target ?: pair.neighbour ?: pair.source!!
  private val appears = pair.change == GlassPlaneChange.Materialize
  private val disappears = pair.change == GlassPlaneChange.Dematerialize
  private val startRect = when {
    appears -> pair.target!!.bounds.planeScale(motion.initialContourScale)
    else -> seed?.bounds ?: origin.bounds
  }
  private val endRect = if (disappears) destination.bounds.planeScale(motion.initialContourScale) else destination.bounds
  private val startRadii = if (appears) destination.radii.planeScale(motion.initialContourScale, startRect)
    else seed?.radii ?: origin.radii
  private val endRadii = if (disappears) destination.radii.planeScale(motion.initialContourScale, endRect)
    else destination.radii
  private val initialAmount = if (appears) 0f else seed?.materialize ?: 1f
  private val targetAmount = if (disappears || pair.target == null && pair.change == GlassPlaneChange.Identity) 0f else 1f
  private val initial = startRect.planeValues() + startRadii.planeValues() + initialAmount
  private val target = endRect.planeValues() + endRadii.planeValues() + targetAmount
  private val velocity = if (appears) FloatArray(9) else seed?.velocity?.let {
    it.bounds.planeValues() + it.radii.planeValues() + it.materialize
  } ?: FloatArray(9)
  var timeScale = 1.0

  // ---- Il donatore e chi accoglie (F3.12a) --------------------------------------------------------------------------

  /**
   * L'**attesa** di un donatore che deve spostarsi, in millisecondi: fino a lì il suo vetro non viaggia (si gonfia e
   * si accende, col carattere del viaggio), poi parte con la molla del piano. La dà il viaggio; zero per tutti gli altri.
   */
  var waitMs = 0.0

  /** Il dondolio di questo volo: il rinculo da chi nasce da lui, la spinta di chi gli confluisce dentro. Null: nessuno. */
  var sway: PlaneSway? = null

  /**
   * Vero se questo volo può dondolare e aspettare: un vetro che c'era e c'è (chi si sposta soltanto, o i due gemelli di
   * una staffetta), con la molla di serie. Chi compare, scompare, nasce o confluisce ha già la sua curva.
   */
  val sways: Boolean get() = motion.spec == null && (pair.change == GlassPlaneChange.Matched || pair.relay)

  /**
   * Vero se questo volo può **aspettare**: parte da fermo (un viaggio ripartito a metà non si ferma ad aspettare:
   * invariante 16) e va da qualche parte.
   */
  val waits: Boolean get() = sways && velocity.all { it == 0f } &&
    (0..7).any { abs(target[it] - initial[it]) > FluidMotion.PlaneSettlePx }

  /**
   * Quanta strada fa il centro di questo vetro, in frazione del suo lato corto e mai più di uno: un donatore che va
   * lontano almeno quanto è alto rilascia chi nasce dopo un pezzo di strada, uno che resta dov'è lo rilascia subito.
   */
  fun travelShare(): Float {
    val short = endRect.minDimension
    if (!(short > 0f)) return 0f
    return (planeCenterDistance(startRect, endRect) / short).coerceIn(0f, 1f)
  }

  // ---- La coreografia della nascita e della confluenza (F3.11) ----------------------------------------------------

  /** Come viaggia la geometria: vedi [PlaneCourse]. Lo decide il cambio; [follow] lo riporta a `Standard` senza un capo. */
  var course: PlaneCourse = when {
    motion.spec != null || pair.relay || pair.neighbour == null -> PlaneCourse.Standard
    pair.change == GlassPlaneChange.BornFromNeighbour -> PlaneCourse.Released
    pair.change == GlassPlaneChange.MergeIntoNeighbour -> PlaneCourse.Merging
    else -> PlaneCourse.Standard
  }
    private set

  /** Il volo che questo segue: il donatore da cui nasce, o il vicino in cui confluisce. Null per un volo `Standard`. */
  var host: PlaneFlight? = null
    private set

  /** Dà il capo al volo; senza capo (non c'è, o la catena torna su se stessa) il volo resta quello di F3.1. */
  fun follow(flight: PlaneFlight?) {
    host = flight
    if (flight == null) course = PlaneCourse.Standard
  }

  /** Dove arriva il volo: il posto del vetro, o quello del vicino per chi vi confluisce. */
  val arrival: Rect get() = endRect

  /** Da dove parte il volo: il posto di prima, o dove si trovava il vetro di un viaggio interrotto. */
  val departure: Rect get() = startRect

  /** Dopo quanto il donatore rilascia questo vetro, in millisecondi: lo dà il viaggio, che conosce i fratelli. */
  var delayMs = 0.0

  /** Da che parte del donatore va chi nasce (0 sinistra, 1 destra, 2 alto, 3 basso): i fratelli di una parte si scaglionano. */
  fun releaseSide(): Int {
    val to = host?.arrival ?: return 0
    val dx = endRect.center.x - to.center.x
    val dy = endRect.center.y - to.center.y
    return if (abs(dx) >= abs(dy)) (if (dx < 0f) 0 else 1) else (if (dy < 0f) 2 else 3)
  }

  /** Quanto è lontano il posto di chi nasce dall'arrivo del donatore, lungo la sua parte: il più lontano parte per primo. */
  fun releaseReach(): Float {
    val to = host?.arrival ?: return 0f
    return max(abs(endRect.center.x - to.center.x), abs(endRect.center.y - to.center.y))
  }

  private var prepared = false

  /** La geometria al rilascio (lati e raggi) e la sua velocità: da qui parte la molla di chi nasce. */
  private val releaseFrom = FloatArray(8)
  private val releaseSpeed = FloatArray(8)

  /** Lo scarto di chi confluisce dal suo posto dentro il vicino, alla partenza, e la sua velocità. */
  private val mergeOffset = FloatArray(8)
  private val mergeSpeed = FloatArray(8)

  /** Il tetto al superamento di ogni canale, in frazione della sua corsa: infinito, nessun tetto. */
  private val overshootCap = FloatArray(8) { Float.POSITIVE_INFINITY }

  /**
   * La corsa di ogni canale su cui si conta il tetto, in pixel: lo scarto alla partenza più la strada che la velocità
   * ereditata gli fa fare. Senza la seconda parte un lato ripartito vicino al suo arrivo ma veloce (un rilascio
   * interrotto) si fermerebbe di colpo.
   */
  private val overshootRun = FloatArray(8)
  private var bounce: PlaneBounce? = null

  /** Vero se il volo rilasciato o che confluisce ha qualcosa da percorrere: altrimenti è già arrivato. */
  private var travels = false

  /**
   * La fine della parte rilasciata, in millisecondi veri: il bilancio della nascita ([GlassPlaneMotion.releaseMs]); zero
   * per un volo `Standard` o fermo. A quell'istante chi è stato rilasciato è tornato dal superamento ed è al suo posto,
   * al bit: è anche il suo riposo a vista (F3.12a: fino a F3.11 il riposo cadeva prima della fine).
   */
  private val courseEndMs: Double get() = if (course != PlaneCourse.Standard && travels) motion.releaseMs else 0.0

  /** Il mezzo periodo della molla di chi è rilasciato e l'istante in cui arriva al suo posto la prima volta, in ms. */
  private var halfMs = 0.0
  private var arriveMs = 0.0

  /** Il rigonfiamento in più con cui il volo comincia (quello del viaggio interrotto) e in quanto si spegne. */
  private val swellEntry: Float = if (motion.spec != null) 0f else seed?.swell?.takeIf { it.isFinite() && it > 0f } ?: 0f
  private val swellFadeMs: Double get() = if (swellEntry > 0f) motion.releaseMs * PlaneSwellCarry else 0.0

  /**
   * Prepara il volo quando il suo capo ha già il suo tempo: per chi nasce, dov'è e come si muove all'istante del
   * rilascio, quale lato è di testa, la molla; per chi confluisce, lo scarto dal vicino alla partenza.
   *
   * Chi è rilasciato viaggia **rigido** (F3.12a): tutti i lati e i raggi hanno la stessa molla
   * ([FluidMotion.PlaneReleaseDamping]) e superano l'arrivo insieme, quindi il centro del vetro, dove sta il contenuto,
   * rimbalza col vetro. Il **lato di testa** (quello dalla parte in cui va il centro) non esce dal contenitore. Chi
   * confluisce non esce dal vicino: la sua spinta la prende il vicino, che si sposta tutto ([PlaneSway]).
   */
  fun prepare() {
    if (prepared) return
    prepared = true
    val leader = host ?: return
    leader.prepare()
    when (course) {
      PlaneCourse.Released -> {
        val value = FloatArray(9)
        val speed = FloatArray(9)
        ride(delayMs, value, speed)
        for (i in 0 until 8) {
          releaseFrom[i] = value[i]
          releaseSpeed[i] = speed[i]
        }
        // La molla sta fra il rilascio e la fine (R4 B2 (b) e (d)): chi è rilasciato dopo è più svelto.
        val damping = FluidMotion.PlaneReleaseDamping.toDouble()
        halfMs = FluidMotion.PlaneReleaseSwing * (motion.releaseMs - delayMs)
        arriveMs = delayMs + planeBounceArrival(damping) * halfMs
        bounce = PlaneBounce(damping, halfMs)
        val overshoot = planeBounceOvershoot(damping)
        for (i in 0 until 8) {
          overshootCap[i] = Float.POSITIVE_INFINITY
          overshootRun[i] = abs(target[i] - releaseFrom[i]) + bounce!!.drift(releaseSpeed[i])
        }
        // Per ogni asse: il lato dalla parte in cui va il centro è di testa, se il contenitore gli lascia posto.
        for ((low, high) in listOf(0 to 2, 1 to 3)) {
          val shift = (target[low] + target[high]) - (releaseFrom[low] + releaseFrom[high])
          if (abs(shift) <= 2f * FluidMotion.PlaneSettlePx) continue
          val lead = if (shift < 0f) low else high
          val run = overshootRun[lead]
          val room = when (lead) {
            0 -> target[0] - limits.left
            1 -> target[1] - limits.top
            2 -> limits.right - target[2]
            else -> limits.bottom - target[3]
          }
          // Un pezzo che sta già fuori dal riquadro del contenitore non ha un tetto da quello.
          val inside = limits.width > 0f && limits.height > 0f && room >= 0f
          overshootCap[lead] = if (inside && run > 0f && room < run * overshoot) max(room / run, PlaneOvershootFloor)
            else Float.POSITIVE_INFINITY
        }
        // Fermo solo chi è già al suo posto alla partenza e al rilascio, senza velocità: non ha niente da percorrere.
        val first = FloatArray(9)
        ride(0.0, first, FloatArray(9))
        travels = (0 until 8).any { i ->
          abs(target[i] - releaseFrom[i]) > FluidMotion.PlaneSettlePx || releaseSpeed[i] != 0f ||
            abs(target[i] - first[i]) > FluidMotion.PlaneSettlePx
        }
      }
      PlaneCourse.Merging -> {
        val value = FloatArray(9)
        val speed = FloatArray(9)
        nest(0.0, value, speed)
        for (i in 0 until 8) {
          mergeOffset[i] = initial[i] - value[i]
          mergeSpeed[i] = velocity[i] - speed[i]
        }
        // Senza attesa, e col colmo dove cade quello di un rilascio (R4 B2 (b): 334 ms tutti e due). Il superamento
        // non esce dall'altra parte del vicino (F3.12a): chi confluisce resta dentro, entro [PlaneMergeSlack], e la
        // sua spinta sposta il vicino tutto intero ([planeSwayOf]), come in Apple.
        bounce = PlaneBounce(FluidMotion.PlaneMergeDamping.toDouble(), motion.releaseMs * FluidMotion.PlaneMergePeak)
        for (i in 0 until 8) overshootCap[i] = PlaneMergeSlack
        for (i in 0 until 8) overshootRun[i] = abs(mergeOffset[i]) + bounce!!.drift(mergeSpeed[i])
        travels = (0 until 8).any { abs(mergeOffset[it]) > FluidMotion.PlaneSettlePx || mergeSpeed[it] != 0f }
      }
      PlaneCourse.Standard -> Unit
    }
  }

  /**
   * Il posto di questo vetro **dentro** il suo capo a [tMs]: della sua misura, o di quella del capo se è più piccolo,
   * dalla parte del posto [place] (a filo del bordo del capo se il posto ne sta fuori, sul posto se il capo lo copre).
   * È la «posizione in volo del donatore» da cui un vetro nasce, e il posto in cui uno che confluisce va a finire. I
   * raggi sono i suoi, mai più stretti di quelli del capo: così non sporge dagli angoli.
   */
  private fun nest(tMs: Double, value: FloatArray, speed: FloatArray, place: Rect = startRect, radii: FloatArray = initial) {
    val leader = host!!.frame(tMs)
    val at = leader.bounds
    val moving = leader.velocity.bounds
    planeNestAxis(at.left, at.right, moving.left, moving.right, place.left, place.right, value, speed, 0, 2)
    planeNestAxis(at.top, at.bottom, moving.top, moving.bottom, place.top, place.bottom, value, speed, 1, 3)
    val around = leader.radii.planeValues()
    for (i in 0 until 4) {
      value[4 + i] = max(radii[4 + i], around[i])
      speed[4 + i] = 0f
    }
  }

  /**
   * Chi nasce, prima del rilascio: sta nel donatore in volo ([nest], dalla parte del suo posto) e se ne sporge un poco
   * ([FluidMotion.PlaneReleaseCreep] della strada che gli resta), con una rampa liscia: parte da fermo e al rilascio è
   * fermo rispetto al donatore. Così la molla di rilascio parte da ferma, come quella adattata alle clip di Apple (R4 B2
   * (b): «parte a 84 ms»), e l'arrivo e il colmo cadono dove dice il suo mezzo periodo. Fino a F3.11 lo sporgersi andava
   * col quadrato del tempo e arrivava al rilascio alla velocità più alta: il vetro partiva 15–20 ms prima di Apple.
   */
  private fun ride(tMs: Double, value: FloatArray, speed: FloatArray) {
    nest(tMs, value, speed, endRect, target)
    if (!(delayMs > 0.0)) return
    val u = (tMs / delayMs).coerceIn(0.0, 1.0)
    val lean = (FluidMotion.PlaneReleaseCreep * u * u * (3.0 - 2.0 * u)).toFloat()
    val leaning = (FluidMotion.PlaneReleaseCreep * 6.0 * u * (1.0 - u) / delayMs * 1000.0).toFloat()
    for (i in 0 until 4) {
      val remaining = target[i] - value[i]
      speed[i] = speed[i] * (1f - lean) + leaning * remaining
      value[i] += lean * remaining
    }
  }

  /**
   * Quanto resta dello scarto dall'arrivo nella coda di un rilascio, e la sua pendenza al secondo: vedi
   * [FluidMotion.PlaneReleaseTailFrom].
   */
  private fun courseTail(tMs: Double, out: FloatArray) {
    val end = courseEndMs
    val from = end * FluidMotion.PlaneReleaseTailFrom
    if (tMs <= from || end <= from) {
      out[0] = 1f
      out[1] = 0f
      return
    }
    val s = ((tMs - from) / (end - from)).toFloat().coerceIn(0f, 1f)
    out[0] = 1f - planeSmoothstep(s)
    out[1] = (-6f * s * (1f - s) / (end - from) * 1000.0).toFloat()
  }

  /** Lo scarto [offset] di un canale dal suo arrivo (velocità [speed]), col tetto al superamento e la coda: in [out]. */
  private fun settle(index: Int, start: Float, offset: Double, speed: Double, fade: FloatArray, out: FloatArray) {
    var value = offset.toFloat()
    var moving = speed.toFloat()
    val limit = overshootCap[index] * overshootRun[index]
    // Oltre l'arrivo (lo scarto ha cambiato segno) il canale rallenta in continuo fino al suo tetto.
    if (limit.isFinite() && limit > 0f && abs(start) > 0.001f && value / start < 0f) {
      val bent = tanh(value / limit)
      value = limit * bent
      moving *= 1f - bent * bent
    }
    out[0] = value * fade[0]
    out[1] = moving * fade[0] + value * fade[1]
  }

  /**
   * Il rigonfiamento in più del volo a [tMs] ([GlassPlanePieceFrame.swell]). Chi è rilasciato si gonfia fra l'arrivo al
   * suo posto e la fine, col colmo a metà ([FluidMotion.PlaneReleaseSwell]: in Apple il colmo del pezzo nato cade a
   * ~0,7–0,77 della nascita, dopo quello del donatore). Quello ereditato da un viaggio interrotto si spegne in
   * [PlaneSwellCarry] del bilancio, da dov'era.
   */
  private fun courseSwell(tMs: Double): Float {
    var level = 0f
    if (swellEntry > 0f && tMs < swellFadeMs) level = swellEntry * (1f - planeSmoothstep((tMs / swellFadeMs).toFloat()))
    val end = courseEndMs
    if (course == PlaneCourse.Released && travels && tMs > arriveMs && tMs < end && end > arriveMs) {
      val s = sin(Math.PI * (tMs - arriveMs) / (end - arriveMs))
      level += (FluidMotion.PlaneReleaseSwell * s * s).toFloat()
    }
    return level.coerceIn(0f, max(FluidMotion.PlaneReleaseSwell, swellEntry))
  }

  /** La geometria (otto canali) di un volo rilasciato o che confluisce a [tMs]. */
  private fun courseGeometry(tMs: Double, value: FloatArray, speed: FloatArray) {
    val spring = bounce
    if (course == PlaneCourse.Released) {
      if (tMs < delayMs) return ride(tMs, value, speed)
      if (spring == null || !travels || tMs >= courseEndMs) {
        for (i in 0 until 8) {
          value[i] = target[i]
          speed[i] = 0f
        }
        return
      }
      val fade = FloatArray(2)
      val free = DoubleArray(2)
      val out = FloatArray(2)
      courseTail(tMs, fade)
      val seconds = (tMs - delayMs) / 1000.0
      for (i in 0 until 8) {
        val start = releaseFrom[i] - target[i]
        spring.at(start.toDouble(), releaseSpeed[i].toDouble(), seconds, free)
        settle(i, start, free[0], free[1], fade, out)
        value[i] = target[i] + out[0]
        speed[i] = out[1]
      }
      return
    }
    // Chi confluisce: il suo posto dentro il vicino in volo, più lo scarto che la molla porta a zero.
    if (tMs <= 0.0) {
      for (i in 0 until 8) {
        value[i] = initial[i]
        speed[i] = velocity[i]
      }
      return
    }
    nest(tMs, value, speed)
    if (spring == null || !travels || tMs >= courseEndMs) return
    val fade = FloatArray(2)
    val free = DoubleArray(2)
    val out = FloatArray(2)
    courseTail(tMs, fade)
    for (i in 0 until 8) {
      spring.at(mergeOffset[i].toDouble(), mergeSpeed[i].toDouble(), tMs / 1000.0, free)
      settle(i, mergeOffset[i], free[0], free[1], fade, out)
      value[i] += out[0]
      speed[i] += out[1]
    }
  }

  /** Vero se l'unione del pezzo cambia in questo viaggio (F3.8a): nasce, si scioglie o passa a un'altra. */
  val unionChanged: Boolean =
    pair.source != null && pair.target != null && pair.source.unionId != pair.target.unionId

  /**
   * **Al suo posto** (F3.8a): lo stesso vetro, alla stessa geometria entro la tolleranza del fermo, con la stessa unione
   * e nessuna velocità ereditata da un viaggio interrotto. Non va da nessuna parte; può però dondolare ([sway]).
   */
  val anchored: Boolean = pair.change == GlassPlaneChange.Matched && !unionChanged && initialAmount == targetAmount &&
    velocity.all { it == 0f } && (0..7).all { abs(target[it] - initial[it]) <= FluidMotion.PlaneSettlePx } &&
    swellEntry == 0f

  /**
   * Fermo: [anchored] e senza dondolio. Un pezzo fermo non ha bisogno del palco. Il donatore di una nascita è al suo
   * posto ma non è fermo (F3.12a): rincula e rimbalza, e il suo contenuto con lui.
   */
  val still: Boolean get() = anchored && sway == null

  fun unitedBefore(other: PlaneFlight): Boolean = planeShareUnion(pair.source, other.pair.source)
  fun unitedAfter(other: PlaneFlight): Boolean = planeShareUnion(pair.target, other.pair.target)

  /** Nella stessa unione prima o dopo il cambio: stanno nello stesso campo, perché il loro collo è di questo viaggio. */
  fun unitedWith(other: PlaneFlight): Boolean = unitedBefore(other) || unitedAfter(other)

  /**
   * `materialize` di una comparsa o di una scomparsa per lente non segue la molla ma una **curva nel tempo** (F3.8a):
   * [GlassPlaneAppearCurve] e [GlassPlaneDisappearCurve], sul bilancio del volo. Con la molla i canali della comparsa
   * (`glassMaterializeChannels`, funzioni del solo `materialize`) correvano nei primi istanti: la comparsa finiva a vista
   * a ~320 ms invece di 430 e la scomparsa a 240–290 invece di 470 (S3). Con una spec dell'app resta la spec.
   */
  private val curve: GlassContentCurve? = when {
    motion.spec != null -> null
    appears -> GlassPlaneAppearCurve
    disappears -> GlassPlaneDisappearCurve
    else -> null
  }
  private val budgetMs: Double = if (disappears) motion.disappearMs else motion.appearMs

  /** Da che punto della curva parte: zero da fermi, il punto col `materialize` di adesso per un volo che riparte. */
  private val curveFrom: Float = curve?.let { planeCurveInverse(it, initialAmount) } ?: 0f

  /** Quanto manca alla fine della curva, in millisecondi veri: zero senza curva. */
  private val curveRemainingMs: Double = if (curve == null) 0.0 else (1.0 - curveFrom) * budgetMs

  /**
   * I canali che seguono la molla del piano: tutti, o i soli otto della geometria quando `materialize` ha la sua curva; di
   * un volo rilasciato o che confluisce (F3.11) il solo `materialize`, perché la geometria ha la sua coreografia.
   */
  private val springChannels: IntRange get() = when {
    course != PlaneCourse.Standard -> PlaneMaterializeChannel..PlaneMaterializeChannel
    curve == null -> 0..PlaneMaterializeChannel
    else -> 0 until PlaneMaterializeChannel
  }

  private var phaseMs = 0.0
  private var phaseFrom = 0f

  /** Di quanto è avanti la fase del contenuto di chi nasce, in millisecondi: vedi [GlassPlanePieceFrame.contentPhase]. */
  private var contentLeadMs = 0.0

  /** L'orologio della fase del volo: vedi [GlassPlanePieceFrame.phase]. [settleMs] è il riposo a vista del viaggio. */
  fun setPhaseClock(settleMs: Double, contentLeadMs: Double = 0.0) {
    this.contentLeadMs = if (pair.change == GlassPlaneChange.BornFromNeighbour && contentLeadMs.isFinite()) contentLeadMs else 0.0
    if (curve != null) {
      phaseMs = budgetMs
      phaseFrom = curveFrom
    } else {
      phaseMs = if (lens) durationMs() else settleMs
      phaseFrom = 0f
    }
  }

  private fun phaseAt(tMs: Double): Float =
    if (!(phaseMs > 0.0)) 1f else (phaseFrom + tMs / phaseMs).toFloat().coerceIn(0f, PlanePhaseCap)

  /**
   * La fine del volo e il suo riposo a vista, in millisecondi veri. Chi confluisce sta dentro il vicino finché quello
   * si muove: finisce e riposa non prima di lui.
   */
  fun durationMs(): Double {
    val spring = durationNanos() / timeScale / 1_000_000.0
    val own = max(max(if (spring > 0.0) waitMs + spring else 0.0, curveRemainingMs), max(courseEndMs, othersEndMs))
    return if (course == PlaneCourse.Merging) max(own, host?.durationMs() ?: 0.0) else own
  }

  fun restMs(): Double {
    val spring = restNanos() / timeScale / 1_000_000.0
    val own = max(max(if (spring > 0.0) waitMs + spring else 0.0, curveRemainingMs), max(courseEndMs, othersEndMs))
    return if (course == PlaneCourse.Merging) max(own, host?.restMs() ?: 0.0) else own
  }

  /** Fin quando durano il dondolio e il rigonfiamento ereditato: il volo non finisce e non riposa prima. */
  private val othersEndMs: Double get() = max(sway?.endMs ?: 0.0, swellFadeMs)

  /**
   * Le molle di serie con cui si contano i tempi, canale per canale (F3.2c): la stessa molla di [FluidMotion.standard],
   * con la soglia del riposo a vista ([FluidMotion.PlaneRestEnvelope] della corsa, mai sotto il mezzo pixel) e quella
   * della fine ([FluidMotion.PlaneSettlePx]). I valori non dipendono dalla soglia: li dà sempre [spring]. Null con una
   * spec dell'app, che porta il suo tempo.
   */
  private val restSprings: Array<FloatSpringSpec>? = if (motion.spec != null) null else Array(initial.size) { index ->
    val tolerance = if (index == PlaneMaterializeChannel) FluidMotion.PlaneRestUnit
      else max(FluidMotion.PlaneRestEnvelope * abs(target[index] - initial[index]), FluidMotion.PlaneSettlePx)
    planeSpring(tolerance)
  }
  private val settleSprings: Array<FloatSpringSpec>? = if (motion.spec != null) null else Array(initial.size) { index ->
    if (index == PlaneMaterializeChannel) PlaneSettleUnitSpring else PlaneSettlePxSpring
  }

  /** Quanto dura, nel tempo della molla, fino all'arrivo al mezzo pixel: la fine del viaggio. */
  fun durationNanos(scale: Double = timeScale): Long = if (pair.change == GlassPlaneChange.Identity) 0L else
    springChannels.maxOf { index -> channelNanos(index, scale, settleSprings) }

  /** Quanto dura, nel tempo della molla, fino al riposo a vista: è su questo che si stira il bilancio. */
  fun restNanos(scale: Double = timeScale): Long = if (pair.change == GlassPlaneChange.Identity) 0L else
    springChannels.maxOf { index -> channelNanos(index, scale, restSprings) }

  private fun channelNanos(index: Int, scale: Double, springs: Array<FloatSpringSpec>?): Long {
    val v = (velocity[index] / scale).toFloat()
    val own = springs?.get(index) ?: return spring.getDurationNanos(
      AnimationVector1D(initial[index]), AnimationVector1D(target[index]), AnimationVector1D(v),
    )
    return own.getDurationNanos(initial[index], target[index], v)
  }

  /** La fine di ogni canale col [timeScale] di adesso, calcolata una volta: [frame] gira a ogni fotogramma. */
  private var endsScale = Double.NaN
  private val ends = LongArray(initial.size)

  private fun channelEnd(index: Int): Long {
    if (endsScale != timeScale) {
      for (i in initial.indices) ends[i] = channelNanos(i, timeScale, settleSprings)
      endsScale = timeScale
    }
    return ends[index]
  }

  fun frame(tMs: Double): GlassPlanePieceFrame {
    // Durante l'attesa il vetro di un donatore non viaggia: l'orologio della sua molla parte dopo.
    val nanos = ((tMs - waitMs) * 1_000_000.0 * timeScale).toLong().coerceAtLeast(0L)
    val value = FloatArray(9)
    val speed = FloatArray(9)
    for (index in springChannels) {
      val a = AnimationVector1D(initial[index])
      val b = AnimationVector1D(target[index])
      val v = AnimationVector1D((velocity[index] / timeScale).toFloat())
      val end = channelEnd(index)
      val finished = pair.change == GlassPlaneChange.Identity || nanos >= end
      // La coda (F3.8a): con la molla di serie lo scarto dall'arrivo si spegne in continuo nell'ultimo tratto del canale,
      // invece di arrivare a mezzo pixel e scattare all'arrivo (il «ritocco di 1 px» ~120 ms dopo il riposo a vista, S3).
      val tailFrom = (end * PlaneTailFrom).toLong()
      val tail = if (restSprings == null || finished || nanos <= tailFrom || end <= tailFrom) 1f
        else 1f - planeSmoothstep((nanos - tailFrom).toFloat() / (end - tailFrom).toFloat())
      value[index] = if (finished) target[index] else if (nanos == 0L) initial[index]
        else target[index] + (spring.getValueFromNanos(nanos, a, b, v).value - target[index]) * tail
      speed[index] = if (finished) 0f else if (nanos == 0L) velocity[index]
        else (spring.getVelocityFromNanos(nanos, a, b, v).value * timeScale).toFloat() * tail
    }
    if (course != PlaneCourse.Standard) courseGeometry(tMs, value, speed)
    // Il dondolio sposta il vetro tutto intero (R4 B2 (b): «si sposta tutto il tondo, non si gonfia un lato»).
    sway?.let { rocking ->
      val shift = FloatArray(4)
      rocking.at(tMs, shift)
      for (axis in 0..1) {
        value[axis] += shift[axis]
        value[axis + 2] += shift[axis]
        speed[axis] += shift[axis + 2]
        speed[axis + 2] += shift[axis + 2]
      }
    }
    if (curve != null) {
      val index = PlaneMaterializeChannel
      val p = (curveFrom + tMs / budgetMs).toFloat()
      val finished = pair.change == GlassPlaneChange.Identity || p >= 1f
      value[index] = if (finished) target[index] else if (tMs <= 0.0) initial[index] else curve.at(p)
      // Al secondo, come le altre: la pendenza della curva sul suo bilancio.
      speed[index] = if (finished) 0f
        else ((curve.at(p + PlaneCurveStep) - curve.at(p)) / PlaneCurveStep / (budgetMs / 1000.0)).toFloat()
    }
    // I vincoli fisici restano veri anche con la velocità contraria di un viaggio interrotto.
    for ((low, high) in listOf(0 to 2, 1 to 3)) {
      if (value[high] < value[low]) {
        val centre = (value[high] + value[low]) / 2f
        value[low] = centre - 0.0001f
        value[high] = centre + 0.0001f
        speed[low] = (speed[low] + speed[high]) / 2f
        speed[high] = speed[low]
      }
    }
    val rect = Rect(value[0], value[1], value[2], value[3])
    val half = rect.minDimension / 2f
    for (index in 4..7) {
      if (value[index] < 0f) speed[index] = 0f
      if (value[index] > half) speed[index] = if (rect.width < rect.height) (speed[2] - speed[0]) / 2f
        else (speed[3] - speed[1]) / 2f
      value[index] = value[index].coerceIn(0f, half)
    }
    if (value[8] < 0f || value[8] > 1f) speed[8] = 0f
    val piece = if (pair.ghost) pair.piece.copy(key = id, id = null) else pair.piece
    return GlassPlanePieceFrame(
      id, piece, rect, FluidCornerRadii(value[4], value[5], value[6], value[7]), value[8].coerceIn(0f, 1f),
      GlassPlaneVelocity(Rect(speed[0], speed[1], speed[2], speed[3]),
        FluidCornerRadii(speed[4], speed[5], speed[6], speed[7]), speed[8]),
      pair.change, pair.matchedBy, pair.neighbour?.key.takeIf { pair.change == GlassPlaneChange.BornFromNeighbour },
      pair.neighbour?.key.takeIf { pair.change == GlassPlaneChange.MergeIntoNeighbour }, pair.target?.bounds ?: pair.source!!.bounds,
      phase = phaseAt(tMs), still = still, unionFrom = pair.source?.unionId, unionTo = pair.target?.unionId,
      relay = pair.relay, swell = courseSwell(tMs), contentPhase = phaseAt(tMs + contentLeadMs),
    )
  }

  /** Il vicino da cui il vetro nasce o in cui confluisce, se ne ha uno. */
  val neighbourKey: Any? get() = pair.neighbour?.key

  /**
   * Dove il vetro sta **per conto suo**: per chi nasce da un vicino il posto d'arrivo, per chi vi confluisce quello di
   * partenza (l'altro capo coincide col vicino, e lì è il vicino a contare); per gli altri tutto ciò che spazzano.
   */
  fun freeBounds(): Rect = when {
    pair.neighbour == null -> sweptBounds()
    pair.target != null && pair.source == null -> if (course == PlaneCourse.Released) reachedBounds() else endRect
    pair.source != null && pair.target == null -> startRect
    else -> sweptBounds()
  }

  /** Il posto d'arrivo di chi è rilasciato, col superamento della sua molla: fin lì il vetro arriva per conto suo. */
  private fun reachedBounds(): Rect {
    val overshoot = planeBounceOvershoot(FluidMotion.PlaneReleaseDamping.toDouble())
    fun beyond(index: Int, outward: Float): Float {
      val run = target[index] - releaseFrom[index]
      return if (run * outward > 0f) overshootRun[index] * min(overshoot, overshootCap[index]) else 0f
    }
    return Rect(endRect.left - beyond(0, -1f), endRect.top - beyond(1, -1f),
      endRect.right + beyond(2, 1f), endRect.bottom + beyond(3, 1f))
  }

  private var flown: Rect? = null

  /**
   * Tutto ciò che spazza un volo rilasciato o che confluisce (F3.11), dalla traiettoria vera: il tratto dentro il capo
   * in volo, lo sporgersi, il superamento e il rimbalzo. Fra due campioni la sagoma esce di meno di un decimo di pixel
   * (gli estremi cadono dove la velocità è zero), e il margine è quello del fermo. Calcolato una volta.
   */
  private fun flownBounds(): Rect = flown ?: run {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    val end = durationMs()
    var t = 0.0
    while (true) {
      val bounds = frame(min(t, end)).bounds
      left = min(left, bounds.left)
      top = min(top, bounds.top)
      right = max(right, bounds.right)
      bottom = max(bottom, bounds.bottom)
      if (t >= end) break
      t += PlaneFlownStepMs
    }
    val margin = FluidMotion.PlaneSettlePx
    Rect(left - margin, top - margin, right + margin, bottom + margin).also { flown = it }
  }

  /** Limite conservativo della risposta libera + impulso iniziale della stessa molla: niente palco che respira. */
  fun sweptBounds(): Rect {
    if (motion.spec != null) return sampledBounds()
    if (course != PlaneCourse.Standard) return flownBounds()
    val damping = FluidMotion.DampingStandard.toDouble()
    val damped = sqrt(1.0 - damping * damping)
    val overshoot = exp(-Math.PI * damping / damped)
    val omega = sqrt(FluidMotion.ResponseStandard.toDouble()) * timeScale
    fun margin(index: Int) = (abs(target[index] - initial[index]) * overshoot +
      abs(velocity[index]) / (omega * damped)).toFloat()
    // Un vetro che dondola (F3.12a) esce al più di quanto il dondolio lo sposta, da ogni parte: in forma chiusa, senza
    // campionare la traiettoria (il palco si calcola nel fotogramma in cui il viaggio parte).
    val rock = FloatArray(4)
    sway?.reach(rock)
    return Rect(
      min(startRect.left, endRect.left) - margin(0) - rock[0], min(startRect.top, endRect.top) - margin(1) - rock[1],
      max(startRect.right, endRect.right) + margin(2) + rock[2], max(startRect.bottom, endRect.bottom) + margin(3) + rock[3],
    )
  }

  /**
   * Il palco di una spec dell'app (F3.2): di una spec qualsiasi non si conosce una forma chiusa dell'oltrepasso, quindi
   * si campiona la traiettoria vera. [PlaneSweptSamples] campioni più i due capi: una spec che oltrepassa fra due
   * campioni lo fa di meno di quanto la curva cambi in un sessantaquattresimo del viaggio.
   */
  private fun sampledBounds(): Rect {
    var left = min(startRect.left, endRect.left)
    var top = min(startRect.top, endRect.top)
    var right = max(startRect.right, endRect.right)
    var bottom = max(startRect.bottom, endRect.bottom)
    val total = durationNanos() / timeScale / 1_000_000.0
    for (step in 0..PlaneSweptSamples) {
      val bounds = frame(total * step / PlaneSweptSamples).bounds
      left = min(left, bounds.left)
      top = min(top, bounds.top)
      right = max(right, bounds.right)
      bottom = max(bottom, bounds.bottom)
    }
    return Rect(left, top, right, bottom)
  }
}

/** Quanti campioni misurano il palco di una spec dell'app: vedi `PlaneFlight.sampledBounds`. */
private const val PlaneSweptSamples = 64

/**
 * Ogni quanti millisecondi si campiona la traiettoria di un rilascio per il suo palco: vedi `PlaneFlight.flownBounds`.
 * Quattro (F3.12a; erano due): una nascita dura 500 ms invece di 345, e il palco si calcola nel fotogramma in cui il
 * viaggio parte. Fra due campioni un lato esce al più di `a·dt²/8`: un decimo di pixel per una corsa di 300 px, sotto il
 * margine del fermo.
 */
private const val PlaneFlownStepMs = 4.0

/** Il ritardo più lungo di un rilascio, in frazione del suo bilancio: oltre, la molla non avrebbe il tempo di tornare. */
private const val PlaneReleaseDelayCap = 0.5f

/** Il tetto più basso al superamento di un lato di testa stretto contro il bordo del contenitore: mai una fermata secca. */
private const val PlaneOvershootFloor = 0.005f

/**
 * La risposta libera di una molla sottosmorzata attorno al suo arrivo, in forma chiusa (F3.11): la molla di rilascio e
 * quella della confluenza. `FluidMotion.standard` non supera l'arrivo, questa lo supera di proposito; i suoi tempi sono
 * dati dal **mezzo periodo** [halfPeriodMs] (dalla partenza al colmo del superamento, per una molla che parte ferma).
 * Fino a F3.11 il mezzo periodo veniva dalla rigidezza della molla del piano stirata su 300 ms (Apple compressa di 1,3
 * volte); da F3.12a è quello misurato a tempo vero, 0,6 del tempo fra il rilascio e la fine (scelta di Alessio: «Come
 * Apple»).
 */
private class PlaneBounce(damping: Double, halfPeriodMs: Double) {
  private val swing = Math.PI / (max(halfPeriodMs, 1.0) / 1000.0)
  private val decay = damping * swing / sqrt(1.0 - damping * damping)

  /** Quanta strada fa fare al più una velocità [speed] alla partenza, in pixel: la sua parte della corsa. */
  fun drift(speed: Float): Float = (abs(speed) / swing).toFloat()

  /** Lo scarto dall'arrivo e la sua velocità [seconds] dopo la partenza da [offset] con velocità [speed]: in [out]. */
  fun at(offset: Double, speed: Double, seconds: Double, out: DoubleArray) {
    val side = (speed + decay * offset) / swing
    val envelope = exp(-decay * seconds)
    val c = cos(swing * seconds)
    val s = sin(swing * seconds)
    out[0] = envelope * (offset * c + side * s)
    out[1] = envelope * (speed * c - (decay * side + swing * offset) * s)
  }
}

/** Di quanto supera l'arrivo una molla smorzata a [damping] che parte ferma, in frazione della corsa. */
private fun planeBounceOvershoot(damping: Double): Float =
  exp(-Math.PI * damping / sqrt(1.0 - damping * damping)).toFloat()

/**
 * Quando una molla smorzata a [damping] che parte ferma passa per l'arrivo la prima volta, in frazione del suo mezzo
 * periodo (0,70 a 0,6): da lì chi è stato rilasciato è al suo posto e lo supera.
 */
private fun planeBounceArrival(damping: Double): Double =
  (Math.PI - atan(sqrt(1.0 - damping * damping) / damping)) / Math.PI

/**
 * Il **dondolio** di un vetro che c'era e c'è (F3.12a): uno spostamento di tutto il vetro, in forma chiusa, che comincia
 * e finisce a zero con velocità zero. Sono i due movimenti che Apple fa fare a chi resta
 * (`artifacts/liquid-glass/sessioni/f3-12a/misure.md`, classe B, a tempo vero, due clip ciascuno):
 * - il **rinculo** del donatore: mentre chi nasce da lui parte, il donatore gli va dietro di 0,077 D (al colmo a metà
 *   della salita della molla di rilascio), poi rimbalza dall'altra parte di 0,047 D e torna;
 * - la **spinta** su chi accoglie: chi confluisce lo sposta tutto intero nel suo verso (0,16 D, l'8,4% della corsa),
 *   e quello torna al suo posto.
 * Il contenuto del vetro lo segue (sta al suo centro): è il «tasto di partenza che rimbalza un pochettino».
 */
private class PlaneSway(private val terms: List<PlaneSwayTerm>) {
  /** Quando il dondolio è finito: da lì il vetro è al suo posto, al bit. */
  val endMs: Double = terms.maxOfOrNull { it.endMs } ?: 0.0

  operator fun plus(other: PlaneSway): PlaneSway = PlaneSway(terms + other.terms)

  /** Lo spostamento a [tMs] lungo x e lungo y, in pixel, e poi le due velocità al secondo: in [out] (quattro posti). */
  fun at(tMs: Double, out: FloatArray) {
    out.fill(0f)
    for (term in terms) term.addTo(tMs, out)
  }

  /**
   * Fin dove il dondolio può spostare il vetro, in pixel: verso sinistra, verso l'alto, verso destra, verso il basso, in
   * [out]. La somma dei colmi di ogni movimento: un tetto, non il massimo esatto (i colmi non cadono insieme).
   */
  fun reach(out: FloatArray) {
    out.fill(0f)
    for (term in terms) {
      val second = if (term.secondMs > term.firstMs) term.second else 0f
      out[term.axis] += max(0f, max(-term.first, -second))
      out[term.axis + 2] += max(0f, max(term.first, second))
    }
  }
}

/**
 * Un movimento del dondolio lungo un asse ([axis]: 0 x, 1 y): da zero a [first] fra [startMs] e [firstMs], poi a
 * [second] a [secondMs] (se viene dopo [firstMs]: altrimenti il secondo colmo non c'è), poi a zero a [endMs]. Ogni
 * tratto è mezzo coseno: nessuno scalino di posizione né di velocità, nemmeno ai due capi.
 */
private class PlaneSwayTerm(
  val axis: Int,
  val first: Float,
  val second: Float,
  val startMs: Double,
  val firstMs: Double,
  val secondMs: Double,
  val endMs: Double,
) {
  fun addTo(tMs: Double, out: FloatArray) {
    if (tMs <= startMs || tMs >= endMs) return
    val value: Double
    val slope: Double
    when {
      tMs <= firstMs -> {
        val span = firstMs - startMs
        val u = (tMs - startMs) / span
        value = first * (1.0 - cos(Math.PI * u)) / 2.0
        slope = first * (Math.PI / 2.0) * sin(Math.PI * u) / span
      }
      tMs <= secondMs -> {
        val span = secondMs - firstMs
        val u = (tMs - firstMs) / span
        value = first + (second - first) * (1.0 - cos(Math.PI * u)) / 2.0
        slope = (second - first) * (Math.PI / 2.0) * sin(Math.PI * u) / span
      }
      else -> {
        val from = if (secondMs > firstMs) second else first
        val begin = max(firstMs, secondMs)
        val span = endMs - begin
        val u = (tMs - begin) / span
        value = from * (1.0 + cos(Math.PI * u)) / 2.0
        slope = -from * (Math.PI / 2.0) * sin(Math.PI * u) / span
      }
    }
    out[axis] += value.toFloat()
    out[axis + 2] += (slope * 1000.0).toFloat()
  }
}

/**
 * Il dondolio che [guests] (chi nasce da [host], chi vi confluisce) danno a [host], o null se non ce n'è. I tempi sono
 * quelli dei voli di chi lo causa: [waitMs] è l'attesa del viaggio, la fine è il bilancio della nascita.
 *
 * Più nati dalla stessa parte tirano insieme, fino a [PlaneSwayGuests] (nel render «Select → Done» il donatore, con due
 * nati, rimbalza del 9% della sua corsa: due volte il rimbalzo di una nascita sola). Chi accoglie più vetri prende una
 * spinta sola: non c'è una clip che dica di più. Nessun dondolio porta il vetro fuori dal contenitore ([limits]).
 */
private fun planeSwayOf(
  host: PlaneFlight,
  guests: List<PlaneFlight>,
  motion: GlassPlaneMotion,
  waitMs: Double,
  limits: Rect,
): PlaneSway? {
  val end = motion.releaseMs
  val place = host.arrival
  val short = place.minDimension
  if (!(short > 0f)) return null
  val terms = ArrayList<PlaneSwayTerm>()
  val born = guests.filter { it.course == PlaneCourse.Released }
  val merging = guests.filter { it.course == PlaneCourse.Merging }
  val recoilShare = min(1f, PlaneSwayGuests / max(born.size, 1))
  for (guest in born) {
    val side = guest.releaseSide()
    val axis = if (side < 2) 0 else 1
    val toward = if (side % 2 == 0) -1f else 1f
    // Verso chi esce, e mai più di un decimo della strada che quello fa: un vetro che nasce accanto non strattona.
    val amount = min(FluidMotion.PlaneDonorRecoil * short, PlaneDonorRecoilReach * guest.releaseReach()) * recoilShare
    if (!(amount > PlaneSwayFloorPx)) continue
    // Il rinculo è di un donatore che resta dov'è (le due nascite di classe B). Uno che se ne va non torna indietro
    // verso chi lascia: nel render «Select → Done» il donatore è al 94% della strada a metà viaggio, senza esitare, e poi
    // supera il suo posto. Quindi il rinculo si spegne con la strada che il donatore fa, e resta il rimbalzo.
    val pull = amount * (1f - host.travelShare())
    val back = min(amount * FluidMotion.PlaneDonorRebound / FluidMotion.PlaneDonorRecoil, planeRoom(place, limits, axis, -toward))
    val half = FluidMotion.PlaneReleaseSwing * (end - guest.delayMs)
    val first = guest.delayMs + 0.5 * half
    val second = first + PlaneSwaySwing * (end - first)
    val start = max(0.0, guest.delayMs - waitMs)
    if (!(start < first && first < second && second < end)) continue
    terms += PlaneSwayTerm(axis, toward * pull, -toward * back, start, first, second, end)
  }
  val pushShare = 1f / max(merging.size, 1)
  for (guest in merging) {
    val from = guest.departure
    // Chi parte già dentro chi lo accoglie (una staffetta interrotta, due vetri sovrapposti) è la stessa sagoma: non
    // ha una corsa con cui spingere.
    if (planeFused(from, host.departure)) continue
    val dx = place.center.x - from.center.x
    val dy = place.center.y - from.center.y
    val axis = if (abs(dx) >= abs(dy)) 0 else 1
    val along = if ((if (axis == 0) dx else dy) < 0f) -1f else 1f
    // La corsa del lato lontano di chi confluisce fino al lato dello stesso nome di chi lo accoglie (R4 B2 (b): 1,94 D).
    val run = when {
      axis == 0 && along > 0f -> place.left - from.left
      axis == 0 -> from.right - place.right
      along > 0f -> place.top - from.top
      else -> from.bottom - place.bottom
    }
    val push = min(planeBounceOvershoot(FluidMotion.PlaneMergeDamping.toDouble()) * run, FluidMotion.PlaneMergePush * short)
    val amount = min(push * pushShare, planeRoom(place, limits, axis, along))
    if (!(amount > PlaneSwayFloorPx)) continue
    val start = end * PlaneMergePushFrom
    val peak = end * FluidMotion.PlaneMergePeak
    if (!(start < peak && peak < end)) continue
    terms += PlaneSwayTerm(axis, along * amount, 0f, start, peak, peak, end)
  }
  return if (terms.isEmpty()) null else PlaneSway(terms)
}

/** Quanto posto ha [place] dentro [limits] dalla parte [direction] dell'asse [axis]; infinito senza un contenitore. */
private fun planeRoom(place: Rect, limits: Rect, axis: Int, direction: Float): Float {
  if (!(limits.width > 0f && limits.height > 0f)) return Float.POSITIVE_INFINITY
  val room = when {
    axis == 0 && direction < 0f -> place.left - limits.left
    axis == 0 -> limits.right - place.right
    direction < 0f -> place.top - limits.top
    else -> limits.bottom - place.bottom
  }
  // Un pezzo che sta già fuori dal riquadro del contenitore non ha un tetto da quello.
  return if (room >= 0f) room else Float.POSITIVE_INFINITY
}

/** Quanti nati dalla stessa parte tirano il donatore al più: vedi [planeSwayOf]. */
private const val PlaneSwayGuests = 2f

/** Il rinculo del donatore non supera questa frazione della strada di chi nasce: scelta, per i vetri che nascono accanto. */
private const val PlaneDonorRecoilReach = 0.1f

/**
 * In che punto fra il colmo del rinculo e la fine cade il colmo del rimbalzo del donatore: 0,68. In Apple il rimbalzo
 * è al colmo a 467 ms e il donatore torna entro 730; qui tutto finisce col bilancio della nascita (500 ms), quindi il
 * rimbalzo è al colmo a ~410 e torna in 90 ms. **Scelta**: il palco non tiene il vetro un quarto di secondo in più per
 * due dp.
 */
private const val PlaneSwaySwing = 0.68

/** Sotto questo spostamento, in pixel, un dondolio non c'è. */
private const val PlaneSwayFloorPx = 0.05f

/** Da quando chi accoglie comincia a essere spinto, in frazione del bilancio: R4 B2 (b), il centro si muove da ~140 ms. */
private const val PlaneMergePushFrom = 0.28

/** Di quanto chi confluisce può oltrepassare il suo posto dentro il vicino, in frazione della corsa: quasi niente. */
private const val PlaneMergeSlack = 0.01f

/** In quanta parte del bilancio si spegne il rigonfiamento in più ereditato da un viaggio interrotto. */
private const val PlaneSwellCarry = 0.3

/**
 * Un asse di `PlaneFlight.nest`: dove sta, fra [lo] e [hi] (che si muovono a [loSpeed] e [hiSpeed]), un tratto lungo
 * quanto [placeLo]..[placeHi] che vuole stare lì. Più lungo del capo: è il capo. Più corto: sul suo posto se il capo lo
 * copre, altrimenti a filo del bordo dalla sua parte, e si muove con quel bordo.
 */
private fun planeNestAxis(
  lo: Float, hi: Float, loSpeed: Float, hiSpeed: Float, placeLo: Float, placeHi: Float,
  value: FloatArray, speed: FloatArray, low: Int, high: Int,
) {
  val size = placeHi - placeLo
  when {
    size >= hi - lo -> {
      value[low] = lo
      value[high] = hi
      speed[low] = loSpeed
      speed[high] = hiSpeed
    }
    placeLo <= lo -> {
      value[low] = lo
      value[high] = lo + size
      speed[low] = loSpeed
      speed[high] = loSpeed
    }
    placeHi >= hi -> {
      value[low] = hi - size
      value[high] = hi
      speed[low] = hiSpeed
      speed[high] = hiSpeed
    }
    else -> {
      value[low] = placeLo
      value[high] = placeHi
      speed[low] = 0f
      speed[high] = 0f
    }
  }
}

/** Il canale di `materialize` fra i nove di un volo (quattro lati, quattro raggi, `materialize`). */
private const val PlaneMaterializeChannel = 8

/** Da che frazione del tempo di un canale il suo scarto dall'arrivo comincia a spegnersi: vedi `PlaneFlight.frame`. */
private const val PlaneTailFrom = 0.7

/** Il passo, in frazione del bilancio, su cui si misura la pendenza della curva di `materialize`. */
private const val PlaneCurveStep = 0.001f

/** La fase più alta che un volo dichiara: oltre il riposo a vista resta la coda che non si vede. */
private const val PlanePhaseCap = 4f

/**
 * `materialize` di una **comparsa** per lente in funzione del tempo, in frazione dei 430 ms di R4 B3 (F3.8a). Le chiavi
 * portano i canali di `glassMaterializeChannels` agli istanti misurati: il contorno pieno (`materialize` 0,3) a 67 ms, la
 * lente e il corpo pieni (0,97) a 230 ms, poi fino a uno senza che si veda altro (il simbolo ha la sua curva sulla fase,
 * `glassPieceContentLook`). Monotona, con derivata continua: nessuna pausa a metà.
 */
internal val GlassPlaneAppearCurve: GlassContentCurve =
  GlassContentCurve(floatArrayOf(0f, 0.156f, 0.535f, 1f), floatArrayOf(0f, 0.3f, 0.97f, 1f))

/**
 * `materialize` di una **scomparsa** per lente in funzione del tempo, in frazione dei 470 ms di R4 B3 (F3.8a): la lente
 * resta piena finché il simbolo se ne va (130 ms), scende a zero entro 400 ms (`materialize` 0,27), poi se ne va il
 * contorno, entro 470.
 */
internal val GlassPlaneDisappearCurve: GlassContentCurve =
  GlassContentCurve(floatArrayOf(0f, 0.277f, 0.851f, 1f), floatArrayOf(1f, 0.97f, 0.27f, 0f))

/**
 * Il punto di [curve] (monotona su 0..1) che vale [value]: da lì riparte un volo interrotto, senza scalini. Per
 * bisezione; fuori dai valori della curva, il capo più vicino.
 */
internal fun planeCurveInverse(curve: GlassContentCurve, value: Float): Float {
  val rising = curve.at(1f) >= curve.at(0f)
  if (!value.isFinite()) return 0f
  if (rising && value <= curve.at(0f) || !rising && value >= curve.at(0f)) return 0f
  if (rising && value >= curve.at(1f) || !rising && value <= curve.at(1f)) return 1f
  var lo = 0f
  var hi = 1f
  repeat(28) {
    val mid = (lo + hi) / 2f
    if ((curve.at(mid) < value) == rising) lo = mid else hi = mid
  }
  return (lo + hi) / 2f
}

private fun planeSmoothstep(x: Float): Float {
  val t = x.coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/**
 * Se due voli stanno nello stesso campo del viaggio per vicinanza (F3.8a). Due vetri che cambiano: se ciò che spazzano
 * è entro [spacing], come sempre. Due vetri **fermi**: mai (a riposo sono separati), salvo [joinStill]. Un fermo e uno
 * che cambia: se il fermo è il vicino da cui l'altro nasce o in cui confluisce, o se l'altro gli arriva entro [spacing]
 * **per conto suo** ([PlaneFlight.freeBounds]): un vetro che nasce dal secondo tasto di una barra parte sovrapposto a lui,
 * e non per questo è vicino al primo.
 */
private fun planeFlightsMeet(a: PlaneFlight, b: PlaneFlight, spacing: Float, joinStill: Boolean): Boolean {
  // Conta chi è al suo posto ([PlaneFlight.anchored]), anche se dondola (F3.12a): il donatore di una nascita che
  // rincula di qualche pixel non per questo tira un collo al tasto fermo che ha dall'altra parte.
  if (joinStill || !a.anchored && !b.anchored) return planeGap(a.sweptBounds(), b.sweptBounds()) <= spacing
  if (a.anchored && b.anchored) return false
  val still = if (a.anchored) a else b
  val moving = if (a.anchored) b else a
  if (moving.neighbourKey != null && moving.neighbourKey == still.pair.piece.key) return true
  return planeGap(still.sweptBounds(), moving.freeBounds()) <= spacing
}

private fun planeShareUnion(a: GlassPlanePiece?, b: GlassPlanePiece?): Boolean =
  a != null && b != null && a.unionId != null && a.unionId == b.unionId

private fun planeSpring(threshold: Float) =
  FloatSpringSpec(FluidMotion.DampingStandard, FluidMotion.ResponseStandard, threshold)

private val PlaneSettlePxSpring = planeSpring(FluidMotion.PlaneSettlePx)
private val PlaneSettleUnitSpring = planeSpring(FluidMotion.PlaneSettleUnit)

/**
 * Di quanto stirare il tempo della molla perché [flights] siano fermi **a vista** in [budgetMs] (F3.2c): vedi la nota sul
 * piano flottante in [FluidMotion]. Fino a F3.2 si stirava la fine dei conti (un centesimo di pixel), e la parte che si
 * vede finiva a meno di metà del bilancio.
 */
private fun planeTimeScale(flights: List<PlaneFlight>, budgetMs: Double): Double {
  var scale = 1.0
  // L'eredità è in px/s reali, perciò la conversione alla spec deve seguire il cambio di unità di tempo.
  repeat(24) {
    val next = flights.maxOf { it.restNanos(scale) } / (budgetMs * 1_000_000.0)
    if (next <= 0.0) return 1.0
    if (abs(next - scale) < 0.000001) return next
    scale = (scale + next) / 2.0
  }
  return scale
}

private fun GlassPlanePiece.sameField(other: GlassPlanePiece) = containerId == other.containerId && variant == other.variant
private fun GlassPlanePiece.sharesUnion(other: GlassPlanePiece) = unionId != null && unionId == other.unionId
private fun Rect.planeValues() = floatArrayOf(left, top, right, bottom)
private fun FluidCornerRadii.planeValues() = floatArrayOf(topLeft, topRight, bottomRight, bottomLeft)
private fun Rect.planeScale(scale: Float): Rect = Rect(center.x - width * scale / 2f, center.y - height * scale / 2f,
  center.x + width * scale / 2f, center.y + height * scale / 2f)
private fun FluidCornerRadii.planeScale(scale: Float, rect: Rect): FluidCornerRadii {
  val values = planeValues().map { min(it * scale, rect.minDimension / 2f) }
  return FluidCornerRadii(values[0], values[1], values[2], values[3])
}
private fun planeBounds(rects: List<Rect>): Rect = if (rects.isEmpty()) Rect.Zero else
  Rect(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
private fun planeGap(a: Rect, b: Rect): Float = hypot(max(0f, max(a.left - b.right, b.left - a.right)),
  max(0f, max(a.top - b.bottom, b.top - a.bottom)))
private fun planeCenterDistance(a: Rect, b: Rect): Float = hypot(a.center.x - b.center.x, a.center.y - b.center.y)

/** Vero se uno dei due riquadri copre il centro dell'altro, bordi compresi: due vetri così sono una sagoma sola. */
private fun planeFused(a: Rect, b: Rect): Boolean {
  fun holds(rect: Rect, x: Float, y: Float) = x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom
  return holds(a, b.center.x, b.center.y) || holds(b, a.center.x, a.center.y)
}

private fun <T> planeComponents(items: List<T>, joins: (T, T) -> Boolean): List<List<T>> {
  val remaining = items.toMutableList()
  val result = ArrayList<List<T>>()
  while (remaining.isNotEmpty()) {
    val group = arrayListOf(remaining.removeAt(0))
    var index = 0
    while (index < group.size) {
      val neighbours = remaining.filter { joins(group[index], it) }
      group.addAll(neighbours)
      remaining.removeAll(neighbours.toSet())
      index++
    }
    result += group
  }
  return result
}

private fun <T> planePartition(items: List<T>, rect: (T) -> Rect, preferred: Int, united: (T, T) -> Boolean): List<List<T>> {
  if (items.size <= preferred) return listOf(items)
  val all = planeBounds(items.map(rect))
  val horizontal = all.width >= all.height
  val sorted = items.sortedWith(compareBy<T> { if (horizontal) rect(it).center.x else rect(it).center.y }
    .thenBy { if (horizontal) rect(it).center.y else rect(it).center.x })
  fun low(item: T) = if (horizontal) rect(item).left else rect(item).top
  fun high(item: T) = if (horizontal) rect(item).right else rect(item).bottom
  val cuts = (1 until sorted.size).filter { cut ->
    items.size > GlassFieldMaxPieces || sorted.take(cut).none { a -> sorted.drop(cut).any { b -> united(a, b) } }
  }
  val cut = cuts.maxWithOrNull(compareBy<Int> { index ->
    sorted.drop(index).minOf(::low) - sorted.take(index).maxOf(::high)
  }.thenBy { -abs(sorted.size - it * 2) }) ?: return listOf(sorted)
  val gap = sorted.drop(cut).minOf(::low) - sorted.take(cut).maxOf(::high)
  if (items.size <= GlassFieldMaxPieces && gap <= 0f) return listOf(sorted)
  return planePartition(sorted.take(cut), rect, preferred, united) +
    planePartition(sorted.drop(cut), rect, preferred, united)
}

/**
 * Intersezione dei segmenti dei centri, compreso un sorpasso su una riga. Due vetri che partono **fusi** (uno copre il
 * centro dell'altro) si dividono, e due che arrivano fusi confluiscono: fra loro non c'è un ordine da incrociare. Fino a
 * F3.8a valeva solo per due centri uguali; ma chi confluisce supera appena il centro di chi lo accoglie (F3.11), e un
 * cambio di stato in quell'istante li prendeva per due vetri che si scambiano di posto, e li rendeva per lente.
 */
private fun planeCrosses(a: PlanePair, b: PlanePair): Boolean {
  if (!a.piece.sameField(b.piece) || a.change == GlassPlaneChange.Identity || b.change == GlassPlaneChange.Identity) return false
  val aFrom = (a.source ?: a.neighbour)?.bounds ?: return false
  val aTo = (a.target ?: a.neighbour)?.bounds ?: return false
  val bFrom = (b.source ?: b.neighbour)?.bounds ?: return false
  val bTo = (b.target ?: b.neighbour)?.bounds ?: return false
  if (planeFused(aFrom, bFrom) || planeFused(aTo, bTo)) return false
  val a0 = aFrom.center
  val a1 = aTo.center
  val b0 = bFrom.center
  val b1 = bTo.center
  fun cross(x: Float, y: Float, u: Float, v: Float) = x * v - y * u
  val dx = a1.x - a0.x
  val dy = a1.y - a0.y
  val ex = b1.x - b0.x
  val ey = b1.y - b0.y
  val determinant = cross(dx, dy, ex, ey)
  if (abs(determinant) < 0.00001f) {
    if (abs(cross(b0.x - a0.x, b0.y - a0.y, dx, dy)) > 0.00001f) return false
    return (a0.x - b0.x) * (a1.x - b1.x) < 0f || (a0.y - b0.y) * (a1.y - b1.y) < 0f
  }
  val alongA = cross(b0.x - a0.x, b0.y - a0.y, ex, ey) / determinant
  val alongB = cross(b0.x - a0.x, b0.y - a0.y, dx, dy) / determinant
  return alongA > 0f && alongA < 1f && alongB > 0f && alongB < 1f
}
