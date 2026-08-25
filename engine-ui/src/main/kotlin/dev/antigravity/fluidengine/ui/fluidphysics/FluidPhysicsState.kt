package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.addContinuousRoundRect

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
 * `progress` non è vincolato a 0..1: le molle della casa hanno overshoot, e l'overshoot in spazio
 * di forma è la vita del morph. I limiti fisici (lati minimi, raggi fra zero e mezzo lato) li
 * tengono le funzioni di interpolazione.
 */
@Stable
class FluidPhysicsState internal constructor(initial: FluidForm) {

  private var formState by mutableStateOf(initial)

  /** La forma a cui la superficie è arrivata, o verso cui sta viaggiando. */
  val form: FluidForm get() = formState

  private val progressAnimatable = Animatable(1f)
  private var activeTransit by mutableStateOf<Transit?>(null)

  /**
   * 0..1 lungo il viaggio corrente (con l'overshoot della molla), 1 a riposo. Da leggere in fase
   * di disegno — dentro `graphicsLayer {}` o un draw block — mai in composizione: è lo stato che
   * cambia a ogni fotogramma.
   */
  val progress: Float get() = if (activeTransit == null) 1f else progressAnimatable.value

  /** Grossolano e sicuro da leggere in composizione: dietro c'è un derivedStateOf. */
  val isMorphing: Boolean by derivedStateOf { activeTransit != null }

  /** Aggiornato dalla composizione: con la scala animazioni a zero, [morphTo] arriva senza viaggiare. */
  internal var reducedMotion: Boolean = false

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
      }
    }

  private val restPlan = PhysicsRenderPlan()
  private var restPlanForm: FluidForm? = null
  private val transitPlan = PhysicsRenderPlan()
  private var transitPlanStamp = Float.NaN
  private var transitPlanTransit: Transit? = null

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
  suspend fun morphTo(
    target: FluidForm,
    // Standard, non fluid: è la molla dei contenitori. Nei fotogrammi delle transizioni Apple i
    // pannelli attaccano in fretta e si posano con una coda lunga, ma non rimbalzano MAI — il
    // rimbalzo è degli elementi piccoli. Una sagoma intera che oltrepassa e torna è la cosa che
    // l'occhio legge come "poco naturale" pur non sapendo dire perché.
    spec: AnimationSpec<Float> = FluidMotion.standard(),
  ) {
    if (activeTransit == null && target == formState) return
    val layover = physicsLayoverFor(currentForm(), target)
    if (layover != null) {
      // La tappa di scalo è servizio, non spettacolo: criticamente smorzata e rapida, così il
      // viaggio si legge come UNO — fondersi e cambiare pelle — e non come due animazioni con
      // una pausa in mezzo.
      morphLeg(layover, FluidMotion.snappy())
    }
    morphLeg(target, spec)
  }

  private suspend fun morphLeg(target: FluidForm, spec: AnimationSpec<Float>) {
    val transit = buildTransit(currentForm(), target)
    formState = target
    activeTransit = transit
    transitPlanStamp = Float.NaN
    try {
      progressAnimatable.snapTo(0f)
      if (reducedMotion) {
        progressAnimatable.snapTo(1f)
      } else {
        progressAnimatable.animateTo(1f, spec)
      }
    } finally {
      // Se un nuovo morphTo ci ha interrotti ha già sostituito il transito: il viaggio vivo è il
      // suo, e cancellarglielo qui lo farebbe scattare a fine corsa.
      if (activeTransit === transit) {
        activeTransit = null
        restPlanForm = null
      }
    }
  }

  /** Arriva senza viaggiare. */
  fun snapTo(target: FluidForm) {
    activeTransit = null
    formState = target
    restPlanForm = null
  }

  /**
   * La geometria di questo istante, come [FluidForm]: a riposo è [form], a metà volo è
   * l'interpolazione vera — il punto di partenza di un ritargeting.
   */
  private fun currentForm(): FluidForm {
    val transit = activeTransit ?: return formState
    val t = progressAnimatable.value
    return when (transit) {
      is SlabTransit -> {
        val slabs = transit.pairs.map { (from, to) ->
          val v = lerpSlab(from, to, t)
          FluidForm.Slab(
            frame = Rect(v[0], v[1], v[0] + v[2], v[1] + v[3]),
            cornerRadii = FluidCornerRadii(v[4], v[5], v[6], v[7]),
          )
        }
        if (slabs.size == 1) slabs[0] else FluidForm.Group(slabs, transit.blendRadius)
      }

      is RingTransit -> {
        val count = transit.rings.count
        val ring = FloatArray(count * 2)
        for (i in ring.indices) ring[i] = lerp(transit.rings.start[i], transit.rings.end[i], t)
        polyFromRing(ring, count)
      }
    }
  }

  private fun buildTransit(origin: FluidForm, target: FluidForm): Transit {
    val originSlabs = slabPiecesOf(origin)
    val targetSlabs = slabPiecesOf(target)
    if (originSlabs != null && targetSlabs != null) {
      val pairs = matchSlabPairs(originSlabs, targetSlabs)
      val blend = maxOf(groupBlendOf(origin), groupBlendOf(target))
      return SlabTransit(pairs, blend)
    }
    // Invariante, non validazione: morphTo instrada le coppie gruppo↔sagoma dallo scalo PRIMA di
    // arrivare qui. Se questo scatta, il bug è a monte.
    check(origin !is FluidForm.Group && target !is FluidForm.Group) {
      "Transito gruppo↔sagoma senza scalo: morphTo doveva passare da physicsLayoverFor."
    }
    return RingTransit(buildMatchedRings(origin, target))
  }

  /** Il piano di resa per il fotogramma corrente. Solo fase di disegno. */
  internal fun ensurePlan(): PhysicsRenderPlan {
    val transit = activeTransit
    if (transit == null) {
      val form = formState
      if (restPlanForm != form) {
        restPlan.clipToBounds = maskInShader
        buildRestPlan(restPlan, form)
        restPlanForm = form
      }
      return restPlan
    }
    val t = progressAnimatable.value
    if (transitPlanTransit !== transit || transitPlanStamp != t) {
      transitPlan.clipToBounds = maskInShader
      buildTransitPlan(transitPlan, transit, t)
      transitPlanTransit = transit
      transitPlanStamp = t
    }
    return transitPlan
  }
}

/** Ricorda uno stato fisico. La forma iniziale conta solo alla prima composizione. */
@Composable
fun rememberFluidPhysicsState(initial: FluidForm): FluidPhysicsState {
  val state = remember { FluidPhysicsState(initial) }
  state.reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  return state
}

// --- Transiti ----------------------------------------------------------------
//
// Interni e non privati: i test del modulo esercitano buildTransitPlan direttamente, perché il
// pezzo di disciplina che protegge — "in viaggio ogni cambio di progresso è un'istantanea nuova,
// a riposo l'istanza è una sola" — è logica pura e va inchiodata senza un frame clock.

internal sealed interface Transit

internal class SlabTransit(
  val pairs: List<Pair<FluidForm.Slab, FluidForm.Slab>>,
  val blendRadius: Float,
) : Transit {
  /**
   * Vero quando due sorgenti viaggiano verso la STESSA destinazione: una fusione. Lì l'overshoot
   * della molla va tagliato sulla geometria: due pezzi che coincidono a t=1 e poi estrapolano a
   * t=1.05 si *ri-separano* per un fotogramma — un doppio bordo che si vede, e che nessuna
   * fusione vera produce.
   */
  val isMerge: Boolean = pairs.map { it.second }.let { targets ->
    targets.size != targets.distinct().size
  }
}

internal class RingTransit(val rings: MatchedRings) : Transit

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
  val anchor = (if (groupToPoly) target else origin) as FluidForm.Poly
  // Raggio pieno: lo scalo è una GOCCIA, non un pannello. Un quadrato arrotondato in mezzo al
  // viaggio si legge come "è tornato al quadrato, poi ha fatto altro" — un'altra forma, non una
  // tappa. Una goccia sul footprint della sagoma si legge come materiale che si raccoglie prima
  // di prendere la pelle nuova, che è esattamente quello che sta succedendo.
  return FluidForm.Slab(
    frame = anchor.frame,
    cornerRadii = FluidCornerRadii.all(anchor.frame.minDimension / 2f),
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
  var vertCount: Int = 0
  val verts = FloatArray(PhysicsMaxVertices * 2)
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
  var bounds: Rect = Rect.Zero

  /** Con più pezzi la tinta deve stare nello shader: il ponte dello smin non ha un path. */
  val tintInShader: Boolean get() = mode == PlanModeSlabs && pieceCount > 1
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

private fun PhysicsRenderPlan.finishSlabs(count: Int, blend: Float) {
  mode = PlanModeSlabs
  pieceCount = count
  blendRadius = blend
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
  shape = FluidPhysicsSilhouetteShape(silhouette, clipToBounds)
}

private fun PhysicsRenderPlan.writeRing(ring: FloatArray, count: Int) {
  mode = PlanModePoly
  pieceCount = 0
  vertCount = count
  ring.copyInto(verts, endIndex = count * 2)
  soften = PolySoftenPx
  silhouette.rewind()
  if (count > 2) {
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
  shape = FluidPhysicsSilhouetteShape(silhouette, clipToBounds)
}

internal fun buildTransitPlan(plan: PhysicsRenderPlan, transit: Transit, t: Float) {
  when (transit) {
    is SlabTransit -> {
      val clamped = if (transit.isMerge) t.coerceAtMost(1f) else t
      plan.silhouette.rewind()
      transit.pairs.forEachIndexed { index, (from, to) ->
        val v = lerpSlab(from, to, clamped)
        plan.writeSlabPiece(index, v)
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
      plan.finishSlabs(transit.pairs.size, transit.blendRadius)
    }

    is RingTransit -> {
      val count = transit.rings.count
      val ring = FloatArray(count * 2)
      for (i in ring.indices) ring[i] = lerp(transit.rings.start[i], transit.rings.end[i], t)
      plan.writeRing(ring, count)
    }
  }
}

internal fun buildRestPlan(plan: PhysicsRenderPlan, form: FluidForm) {
  when (form) {
    is FluidForm.Slab -> {
      plan.silhouette.rewind()
      writeRestSlab(plan, form, 0)
      plan.finishSlabs(1, 0f)
    }

    is FluidForm.Group -> {
      plan.silhouette.rewind()
      form.pieces.forEachIndexed { index, piece ->
        writeRestSlab(plan, piece as FluidForm.Slab, index)
      }
      plan.finishSlabs(form.pieces.size, form.blendRadius)
    }

    is FluidForm.Poly -> {
      val ring = restRing(form)
      plan.writeRing(ring, ring.size / 2)
    }
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
