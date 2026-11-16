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
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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
) {
  init {
    require(listOf(contextMs, appearMs, disappearMs).all { it.isFinite() && it > 0.0 })
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
   * di tutti.
   */
  val settleMs: Double

  init {
    val pairing = planePairs(from, to, seeds)
    crossingIds = pairing.crossingIds
    flights = pairing.pairs.map { pair -> PlaneFlight(pair, seeds, motion) }
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
    flights.forEach { it.setPhaseClock(settle) }
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
      .sortedWith(compareBy({ planeGap(it.bounds, b.bounds) }, { planeCenterDistance(it.bounds, b.bounds) }))
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
      .sortedBy { planeCenterDistance(a.bounds, it.bounds) }
      .firstOrNull { neighbour -> pairs.none { planeCrosses(PlanePair(a, neighbour, GlassPlaneChange.MergeIntoNeighbour), it) } }
    pairs += PlanePair(a, null, when {
      a.transition == GlassPlaneTransition.Identity -> GlassPlaneChange.Identity
      neighbour != null -> GlassPlaneChange.MergeIntoNeighbour
      else -> GlassPlaneChange.Dematerialize
    }, neighbour = neighbour, ghost = a.id in crossingIds || to.pieces.any {
      it.key == a.key || a.id != null && it.id == a.id && sameDomain(a, it)
    })
  }
  return PlanePairing(pairs, crossingIds)
}

private class PlaneFlight(val pair: PlanePair, seeds: Map<Any, GlassPlanePieceFrame>, private val motion: GlassPlaneMotion) {
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

  /** Vero se l'unione del pezzo cambia in questo viaggio (F3.8a): nasce, si scioglie o passa a un'altra. */
  val unionChanged: Boolean =
    pair.source != null && pair.target != null && pair.source.unionId != pair.target.unionId

  /**
   * Fermo (F3.8a): lo stesso vetro, alla stessa geometria entro la tolleranza del fermo, con la stessa unione e nessuna
   * velocità ereditata da un viaggio interrotto. Un pezzo fermo non ha bisogno del palco.
   */
  val still: Boolean = pair.change == GlassPlaneChange.Matched && !unionChanged && initialAmount == targetAmount &&
    velocity.all { it == 0f } && (0..7).all { abs(target[it] - initial[it]) <= FluidMotion.PlaneSettlePx }

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

  /** I canali che seguono la molla: tutti, o i soli otto della geometria quando `materialize` ha la sua curva. */
  private val springChannels: IntRange = if (curve == null) 0..PlaneMaterializeChannel else 0 until PlaneMaterializeChannel

  private var phaseMs = 0.0
  private var phaseFrom = 0f

  /** L'orologio della fase del volo: vedi [GlassPlanePieceFrame.phase]. [settleMs] è il riposo a vista del viaggio. */
  fun setPhaseClock(settleMs: Double) {
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

  /** La fine del volo e il suo riposo a vista, in millisecondi veri. */
  fun durationMs(): Double = max(durationNanos() / timeScale / 1_000_000.0, curveRemainingMs)
  fun restMs(): Double = max(restNanos() / timeScale / 1_000_000.0, curveRemainingMs)

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
    val nanos = (tMs * 1_000_000.0 * timeScale).toLong().coerceAtLeast(0L)
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
    pair.target != null && pair.source == null -> endRect
    pair.source != null && pair.target == null -> startRect
    else -> sweptBounds()
  }

  /** Limite conservativo della risposta libera + impulso iniziale della stessa molla: niente palco che respira. */
  fun sweptBounds(): Rect {
    if (motion.spec != null) return sampledBounds()
    val damping = FluidMotion.DampingStandard.toDouble()
    val damped = sqrt(1.0 - damping * damping)
    val overshoot = exp(-Math.PI * damping / damped)
    val omega = sqrt(FluidMotion.ResponseStandard.toDouble()) * timeScale
    fun margin(index: Int) = (abs(target[index] - initial[index]) * overshoot +
      abs(velocity[index]) / (omega * damped)).toFloat()
    return Rect(min(startRect.left, endRect.left) - margin(0), min(startRect.top, endRect.top) - margin(1),
      max(startRect.right, endRect.right) + margin(2), max(startRect.bottom, endRect.bottom) + margin(3))
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
  if (joinStill || !a.still && !b.still) return planeGap(a.sweptBounds(), b.sweptBounds()) <= spacing
  if (a.still && b.still) return false
  val still = if (a.still) a else b
  val moving = if (a.still) b else a
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

/** Intersezione dei segmenti dei centri, compreso un sorpasso su una riga; un seme comune è una divisione. */
private fun planeCrosses(a: PlanePair, b: PlanePair): Boolean {
  if (!a.piece.sameField(b.piece) || a.change == GlassPlaneChange.Identity || b.change == GlassPlaneChange.Identity) return false
  val a0 = (a.source ?: a.neighbour)?.bounds?.center ?: return false
  val a1 = (a.target ?: a.neighbour)?.bounds?.center ?: return false
  val b0 = (b.source ?: b.neighbour)?.bounds?.center ?: return false
  val b1 = (b.target ?: b.neighbour)?.bounds?.center ?: return false
  if (a0 == b0 || a1 == b1) return false
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
