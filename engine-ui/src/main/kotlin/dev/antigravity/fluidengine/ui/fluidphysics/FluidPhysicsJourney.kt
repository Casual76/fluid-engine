package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FloatAnimationSpec
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import kotlin.math.max
import kotlin.math.min

/**
 * I viaggi di serie di [FluidPhysicsState.morphTo].
 *
 * **Il default e' una molla sola** ([Spring]), dalla 3.0: parte alla velocita' del momento — zero da
 * fermo, quella del viaggio interrotto in un ritargeting, quella del gesto se il chiamante la passa —
 * e si posa con un oltrepasso che la geometria rende come un gonfiore uniforme attorno al centro del
 * traguardo (mai uno stiramento lungo il viaggio). E' il movimento delle gocce di Kyant
 * (`DampedDragAnimation`: molle sottosmorzate che ereditano la velocita'), ed e' quello che il
 * contratto chiede: "niente rincorsa".
 *
 * La coreografia di prima — rincorsa in accelerazione, poi una molla secca che rimbalza — resta come
 * preset esplicito, [House]: e' giusta per un morph dimostrativo avviato da un tocco secco (il
 * Playground), dove la partenza lenta si vede e fa parte dello spettacolo, ed e' sbagliata per tutto
 * cio' che l'utente sta aspettando.
 */
object FluidPhysicsJourney {

  /**
   * La molla di serie: smorzamento 0,62 e risposta 0,36 s, i token del menu che si apre
   * ([FluidMotion.MenuOpenDamping], [FluidMotion.MenuOpenStiffness]).
   *
   * Perche' questi: le gocce di Kyant viaggiano su molle a smorzamento 0,6-0,7 e rigidita' 250 (una
   * risposta di 0,40 s), e il menu della 3.0 e' gia' tarato sullo stesso gesto che un morph fa piu'
   * spesso — un tasto che diventa un pannello — con un picco d'oltrepasso dell'8,4%
   * ([FluidMotion.MenuPeakOvershoot]). Una superficie che si trasforma col proprio orologio e una
   * guidata dal menu devono muoversi uguale, o lo stesso tasto si apre in due modi.
   */
  val Spring: AnimationSpec<Float> = spring(
    dampingRatio = FluidMotion.MenuOpenDamping,
    stiffness = FluidMotion.MenuOpenStiffness,
    visibilityThreshold = JourneyVisibilityThreshold,
  )

  /**
   * La coreografia della casa (fino alla 2.13 il default): **parte piano, accelera, e si posa con
   * un rimbalzo leggero di molla.** Una molla sola non sa partire piano (nasce alla massima
   * accelerazione), quindi sono due tempi in una spec: una rincorsa in ease-in fino a
   * [JourneyRunUpEnd] del viaggio, e da li' una molla sottosmorzata che eredita la velocita' con cui
   * la rincorsa esce. La velocita' iniziale passata a `animateTo` non conta: la partenza lenta e' il
   * punto di questo preset.
   */
  val House: AnimationSpec<Float> = HouseJourneySpec()
}

/** Sotto questo scarto la molla e' arrivata: un millesimo di viaggio, un pixel su mille. */
internal const val JourneyVisibilityThreshold = 0.001f

// --- La coreografia della casa ------------------------------------------------
//
// "Parte piano, accelera, rimbalza alla fine e arriva nella figura finale." I numeri sono la
// taratura di quella frase: la rincorsa copre i quattro quinti del viaggio in 180 ms con un easing
// che finisce a pendenza ~2.4 (la velocita' consegnata alla molla), e la molla sottosmorzata
// trasforma quella velocita' in UN oltrepasso visibile che si posa.

/** Dove finisce la rincorsa e comincia la molla, in frazione di viaggio. */
internal const val JourneyRunUpEnd = 0.80f

/** Durata della rincorsa. Accorciata su richiesta: "hanno solo bisogno di essere accelerate". */
internal const val JourneyRunUpMillis = 180

/** Piano in partenza, ripido in uscita: la pendenza finale E' il rimbalzo. */
internal val JourneyRunUpEasing = CubicBezierEasing(0.55f, 0f, 0.72f, 0.4f)

/** Sottosmorzata quanto basta per UN rimbalzo leggero, non un tremolio. */
internal const val JourneyBounceDamping = 0.58f

/** La rigidita' della posata: quasi istantanea — la coda non deve trascinare il viaggio. */
internal val JourneyBounceStiffness = FluidMotion.ResponseInstant

/**
 * La casa come una spec sola: rincorsa e molla, con la velocita' passata dall'una all'altra.
 *
 * Prima erano due `animateTo` di fila, e l'`Animatable` portava la velocita' dal primo al secondo.
 * Una spec sola si puo' passare dove si passa una molla qualsiasi (`morphTo(target, House)`), ed e'
 * l'unico modo di tenerla come preset invece che come ramo del codice.
 */
internal class HouseJourneySpec : FloatAnimationSpec {

  private val bounce = FloatSpringSpec(
    dampingRatio = JourneyBounceDamping,
    stiffness = JourneyBounceStiffness,
    visibilityThreshold = JourneyVisibilityThreshold,
  )

  private val runUpNanos = JourneyRunUpMillis * 1_000_000L

  private fun runUpEnd(initialValue: Float, targetValue: Float): Float =
    initialValue + (targetValue - initialValue) * JourneyRunUpEnd

  /** La velocita' con cui la rincorsa esce, in unita' al secondo: la pendenza finale dell'easing. */
  private fun exitVelocity(initialValue: Float, targetValue: Float): Float =
    (runUpEnd(initialValue, targetValue) - initialValue) * easingSlope(1f) / (JourneyRunUpMillis / 1000f)

  override fun getValueFromNanos(
    playTimeNanos: Long,
    initialValue: Float,
    targetValue: Float,
    initialVelocity: Float,
  ): Float {
    val end = runUpEnd(initialValue, targetValue)
    if (playTimeNanos < runUpNanos) {
      val f = (playTimeNanos.toFloat() / runUpNanos).coerceIn(0f, 1f)
      return initialValue + (end - initialValue) * JourneyRunUpEasing.transform(f)
    }
    return bounce.getValueFromNanos(
      playTimeNanos - runUpNanos,
      end,
      targetValue,
      exitVelocity(initialValue, targetValue),
    )
  }

  override fun getVelocityFromNanos(
    playTimeNanos: Long,
    initialValue: Float,
    targetValue: Float,
    initialVelocity: Float,
  ): Float {
    val end = runUpEnd(initialValue, targetValue)
    if (playTimeNanos < runUpNanos) {
      val f = (playTimeNanos.toFloat() / runUpNanos).coerceIn(0f, 1f)
      return (end - initialValue) * easingSlope(f) / (JourneyRunUpMillis / 1000f)
    }
    return bounce.getVelocityFromNanos(
      playTimeNanos - runUpNanos,
      end,
      targetValue,
      exitVelocity(initialValue, targetValue),
    )
  }

  override fun getDurationNanos(initialValue: Float, targetValue: Float, initialVelocity: Float): Long =
    runUpNanos + bounce.getDurationNanos(
      runUpEnd(initialValue, targetValue),
      targetValue,
      exitVelocity(initialValue, targetValue),
    )

  override fun getEndVelocity(initialValue: Float, targetValue: Float, initialVelocity: Float): Float = 0f

  /** La pendenza dell'easing in [f], per differenze: un'easing di Bezier non ha una derivata pubblica. */
  private fun easingSlope(f: Float): Float {
    val h = 0.002f
    val a = (f - h).coerceIn(0f, 1f)
    val b = (f + h).coerceIn(0f, 1f)
    if (b <= a) return 0f
    return (JourneyRunUpEasing.transform(b) - JourneyRunUpEasing.transform(a)) / (b - a)
  }
}

// --- La velocita' che un viaggio eredita ----------------------------------------

/**
 * La velocita' di partenza di un viaggio che ne interrompe un altro, in unita' di progresso al
 * secondo del viaggio NUOVO.
 *
 * Il viaggio interrotto si muoveva a [velocity] (progresso al secondo) da [previousStart] a
 * [previousEnd]: in geometria, i quattro lati del riquadro correvano a `velocity * (fine - inizio)`.
 * Il viaggio nuovo va da [nextStart] a [nextEnd], e la sua velocita' di partenza e' la proiezione di
 * quel moto sulla sua direzione: avanti se il dito ha solo spostato il traguardo, indietro (negativa)
 * se l'ha rovesciato — la sagoma prosegue un istante per la strada che aveva e poi torna, come una
 * cosa con una massa. Senza, ogni ritargeting ripartiva da fermo: uno scalino di velocita' a ogni
 * cambio d'idea, che e' esattamente il "non si muove in modo naturale".
 *
 * Tenuta fra [MinInheritedVelocity] e [MaxInheritedVelocity]: un viaggio nuovo cortissimo trasforma
 * una velocita' normale in una enorme, e una molla lanciata cosi' non rimbalza, scappa.
 */
internal fun inheritedProgressVelocity(
  previousStart: Rect,
  previousEnd: Rect,
  velocity: Float,
  nextStart: Rect,
  nextEnd: Rect,
): Float {
  if (!velocity.isFinite() || velocity == 0f) return 0f
  val dl = nextEnd.left - nextStart.left
  val dt = nextEnd.top - nextStart.top
  val dr = nextEnd.right - nextStart.right
  val db = nextEnd.bottom - nextStart.bottom
  val norm = dl * dl + dt * dt + dr * dr + db * db
  // Un viaggio piu' corto di un pixel non ha una direzione: niente da ereditare.
  if (!(norm >= 1f)) return 0f
  val gl = velocity * (previousEnd.left - previousStart.left)
  val gt = velocity * (previousEnd.top - previousStart.top)
  val gr = velocity * (previousEnd.right - previousStart.right)
  val gb = velocity * (previousEnd.bottom - previousStart.bottom)
  val projected = (gl * dl + gt * dt + gr * dr + gb * db) / norm
  if (!projected.isFinite()) return 0f
  return projected.coerceIn(MinInheritedVelocity, MaxInheritedVelocity)
}

/**
 * Il tetto della velocita' ereditata in avanti, in viaggi al secondo. La molla di serie, partita da
 * ferma, tocca al massimo ~6 viaggi al secondo: oltre, l'eredita' sarebbe un lancio.
 */
internal const val MaxInheritedVelocity = 6f

/** Il tetto all'indietro: abbastanza per proseguire un istante, non per tornare a meta' strada. */
internal const val MinInheritedVelocity = -3f

// --- L'oltrepasso come gonfiore --------------------------------------------------

/**
 * Quanto si gonfia il traguardo per ogni unita' d'oltrepasso della molla: lo stesso guadagno del
 * menu ([FluidMotion.MenuInflationGain]), perche' il "pop" di un pannello e' uno solo.
 */
internal const val MorphInflationGain = FluidMotion.MenuInflationGain

/**
 * Il gonfiore massimo, in frazione della taglia. Con la molla di serie il picco e' 0,6 x 8,4% = 5%:
 * il tetto non scatta, e trattiene solo una molla piu' viva o una velocita' ereditata alta.
 */
internal const val MorphMaxInflation = 0.08f

/**
 * Di quanto, al massimo, ogni lato della sagoma puo' uscire dal traguardo, in dp: meno del margine
 * di 16 dp che un pannello a tutta larghezza tiene dallo schermo, cosi' al picco non lo tocca mai.
 */
internal const val MorphInflationCapDp = 12f

/**
 * La scala uniforme del traguardo a progresso [t]: 1 fino al traguardo, e oltre
 * `1 + (t - 1) * guadagno`, tenuta sotto [MorphMaxInflation] e sotto il tetto per lato
 * ([capPx], in pixel: `MorphInflationCapDp` per la densita').
 *
 * **Una scala, non due**: il lato lungo decide il tetto in pixel e il corto lo segue, cosi' il
 * gonfiore resta uniforme — un pannello che al picco cambiasse proporzioni sarebbe di nuovo uno
 * stiramento, solo piu' piccolo.
 */
internal fun morphOvershootScale(t: Float, frame: Rect, capPx: Float): Float {
  if (!(t > 1f)) return 1f
  val raw = (t - 1f) * MorphInflationGain
  val longSide = max(frame.width, frame.height)
  val pxCap = if (longSide > 0f && capPx.isFinite()) 2f * capPx / longSide else Float.POSITIVE_INFINITY
  val growth = min(raw, min(MorphMaxInflation, pxCap))
  return 1f + growth.coerceAtLeast(0f)
}

/**
 * [slab] scalato attorno a [center]: il riquadro di [scaleX] e [scaleY], la posizione rispetto al
 * centro dello stesso fattore (un pezzo di un gruppo si allontana dal centro dell'unione mentre
 * cresce), i raggi del fattore minore — una capsula resta una capsula — e mai oltre il mezzo lato.
 */
internal fun inflateSlab(
  slab: FluidForm.Slab,
  centerX: Float,
  centerY: Float,
  scaleX: Float,
  scaleY: Float,
): FluidForm.Slab {
  if (scaleX == 1f && scaleY == 1f) return slab
  val f = slab.frame
  val frame = Rect(
    left = centerX + (f.left - centerX) * scaleX,
    top = centerY + (f.top - centerY) * scaleY,
    right = centerX + (f.right - centerX) * scaleX,
    bottom = centerY + (f.bottom - centerY) * scaleY,
  )
  val half = frame.minDimension / 2f
  val radiusScale = min(scaleX, scaleY)
  val r = slab.cornerRadii
  return FluidForm.Slab(
    frame = frame,
    cornerRadii = FluidCornerRadii(
      topLeft = (r.topLeft * radiusScale).coerceIn(0f, half),
      topRight = (r.topRight * radiusScale).coerceIn(0f, half),
      bottomRight = (r.bottomRight * radiusScale).coerceIn(0f, half),
      bottomLeft = (r.bottomLeft * radiusScale).coerceIn(0f, half),
    ),
    smoothing = slab.smoothing,
  )
}

/** Un anello di vertici scalato attorno a ([centerX], [centerY]), in un array nuovo. */
internal fun inflateRing(ring: FloatArray, count: Int, centerX: Float, centerY: Float, scale: Float): FloatArray {
  val out = FloatArray(count * 2)
  for (i in 0 until count) {
    out[i * 2] = centerX + (ring[i * 2] - centerX) * scale
    out[i * 2 + 1] = centerY + (ring[i * 2 + 1] - centerY) * scale
  }
  return out
}

/** Il riquadro di un anello di vertici; [Rect.Zero] se vuoto. */
internal fun ringBounds(ring: FloatArray, count: Int): Rect {
  if (count <= 0) return Rect.Zero
  var left = Float.POSITIVE_INFINITY
  var top = Float.POSITIVE_INFINITY
  var right = Float.NEGATIVE_INFINITY
  var bottom = Float.NEGATIVE_INFINITY
  for (i in 0 until count) {
    left = min(left, ring[i * 2])
    top = min(top, ring[i * 2 + 1])
    right = max(right, ring[i * 2])
    bottom = max(bottom, ring[i * 2 + 1])
  }
  return Rect(left, top, right, bottom)
}
