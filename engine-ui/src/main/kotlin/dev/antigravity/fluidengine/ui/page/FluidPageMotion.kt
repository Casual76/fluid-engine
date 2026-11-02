/*
 * I numeri e le regole delle pagine (3.0, fase 8).
 *
 * Una parte e' portata da LibrePods, **GPL-3.0**:
 *
 *   me.kavishdevar.librepods.presentation.navigation.SwipeBack.kt
 *     righe 56-60   (la direzione di un cambio di pila: indietro se la cima c'era gia')
 *     righe 209-210 (la parallasse della pagina sotto: −(W/3)·(1 − p))
 *     riga  232     (la regola del rilascio: 0,35 W oppure una spinta)
 *   me.kavishdevar.librepods.presentation.components.primitives.StyledScaffold.kt
 *     righe 111-127 (gli angoli della carta sono quelli del telefono)
 *     righe 227-241 (ombra di 36 dp, nera su superficie chiara, grigia su scura)
 *   https://github.com/librepods-org/librepods, branch `android/rewrite`, commit 3d4d270.
 *
 * Licenza: GPL-3.0, https://www.gnu.org/licenses/gpl-3.0.html; vedi LICENSES/LibrePods.md.
 *
 * **Cosa abbiamo cambiato.**
 *   - il rilascio ha un tetto in dp (sui tablet 0,35 W e' mezzo braccio) e una spinta *contraria*
 *     annulla anche oltre la distanza; la spinta e' in dp/s, non in px/s, cosi' non dipende dalla
 *     densita' dello schermo;
 *   - il back di sistema si legge dalla posizione del dito (`touchX`), mai dal `progress`, che il
 *     sistema smussa e scala: la pagina sta sotto il dito come nel trascinamento in-app;
 *   - lo stile "rimpicciolisce e scivola", la classificazione di sostituzioni, salti di piu' pagine e
 *     passi fra schede, e gli angoli che restano tondi solo dove la pagina tocca davvero un angolo
 *     dello schermo (un pannello di dettaglio non ha gli angoli del telefono);
 *   - le molle (in [FluidPageMotion.page]) al posto del `tween(150)` di durata fissa: partono dalla
 *     velocita' del dito.
 */
package dev.antigravity.fluidengine.ui.page

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Dove sta la pagina sopra in un fotogramma: spostamento lungo l'uscita (px, ≥ 0) e scala. */
@Immutable
data class FluidPageFrame(val translation: Float, val scale: Float)

/** I raggi della carta, in px, in coordinate assolute (non start/end). */
@Immutable
data class FluidPageCorners(
  val topLeft: Float = 0f,
  val topRight: Float = 0f,
  val bottomRight: Float = 0f,
  val bottomLeft: Float = 0f,
) {
  val isZero: Boolean get() = topLeft <= 0f && topRight <= 0f && bottomRight <= 0f && bottomLeft <= 0f

  companion object {
    val Zero = FluidPageCorners()
  }
}

/** Da quale bordo e' partito il gesto di sistema. */
enum class FluidSwipeEdge { Left, Right, None }

/** Che cosa e' cambiato fra due pile. */
enum class FluidStackChangeKind {
  /** La cima e' la stessa (o una delle due pile e' vuota). */
  None,

  /** E' arrivata una cima nuova e la vecchia e' ancora nella pila. */
  Push,

  /** La nuova cima c'era gia', sotto: si torna indietro di una o piu' pagine. */
  Pop,

  /** Cima nuova, la vecchia non c'e' piu' e le due non sono pari: l'host la tratta come un push. */
  Replace,

  /** Le due cime sono pari ([FluidPeerMove]): passo laterale fra schede. */
  Peer,
}

@Immutable
data class FluidStackChange<K>(
  val kind: FluidStackChangeKind,
  val from: K?,
  val to: K?,
  /** Per [FluidStackChangeKind.Peer]: +1 se [to] viene dopo [from], −1 se prima. Altrimenti 0. */
  val direction: Int = 0,
  /** Per un salto di piu' pagine indietro: quante pagine spariscono senza farsi vedere. */
  val skipped: Int = 0,
)

/**
 * Costanti e funzioni pure delle pagine. **Un orologio solo**: `travel`, i pixel che la pagina sopra
 * ha percorso verso l'uscita; ogni cosa che si vede e' una funzione di travel, cosi' il dito, la
 * molla e il tasto indietro disegnano la stessa cosa nello stesso punto.
 */
object FluidPageMotion {

  /** Quanto arretra la pagina sotto, in frazione della larghezza (LibrePods SwipeBack.kt:209-210). */
  const val BelowParallaxFraction: Float = 1f / 3f

  /** La zona del bordo che reclama il dito prima dei figli (navigazione a 3 tasti). */
  const val EdgeZoneDp: Float = 20f

  /**
   * Oltre questa frazione della larghezza un rilascio lento conferma (LibrePods: 0,35 W).
   *
   * Con il tetto [CommitCapDp]: su un tablet di 800 dp il 35% sono 280 dp, un braccio intero per
   * tornare indietro di una pagina.
   */
  const val CommitFraction: Float = 0.35f
  const val CommitCapDp: Float = 180f

  /**
   * La spinta che decide da sola, in dp/s. LibrePods usa 1000 px/s, ≈ 380 dp/s sul suo telefono: in
   * dp la soglia vale uguale su ogni densita'. Una spinta all'indietro della stessa forza annulla
   * anche se la pagina e' gia' oltre la meta': il dito ha cambiato idea, e la molla gli da' ragione.
   */
  const val FlingDpPerSecond: Float = 400f

  /** [FluidPageTransition.ShrinkSlide]: il primo 30% della corsa rimpicciolisce e basta. */
  const val ShrinkTravelFraction: Float = 0.30f

  /** Quanto rimpicciolisce: fino al 90%. */
  const val ShrinkAmount: Float = 0.10f

  /**
   * Dove finisce la corsa di ShrinkSlide ed Expand, in larghezze. Rimpicciolita al 90% e spostata di
   * 0,95 W, la pagina ha il bordo sinistro esattamente sul bordo destro dello schermo.
   */
  const val ShrinkExitFraction: Float = 1.25f

  /** Da dove parte [FluidPageTransition.Expand]: la stessa scala che aveva la crescita di rotta. */
  const val ExpandInitialScale: Float = 0.91f

  /** L'ombra della carta (LibrePods StyledScaffold.kt:227-241). */
  const val CardElevationDp: Float = 36f

  /**
   * Sotto mezzo pixel di corsa la pagina e' a riposo: niente angoli, niente ombra. La carta esiste
   * solo mentre si muove, cosi' a riposo il layer non ritaglia niente.
   */
  const val CardThresholdPx: Float = 0.5f

  /** Due eventi di sistema con il dito fermo entro questi pixel e il progresso in calo: rilasciato. */
  const val SystemStillPx: Float = 0.5f

  /** La pagina sopra resta "davanti" (vetro, barra) fino a meta' della sua uscita. */
  const val FrontHandoff: Float = 0.5f

  /**
   * La risposta della molla delle pagine, in secondi: fra `standard` (0,38) e `snappy` (0,30).
   * Una pagina e' la cosa piu' grande che si muove, ma un push non deve sembrare una tenda.
   */
  const val ResponsePageSeconds: Double = 0.34
  val ResponsePage: Float = FluidMotion.stiffnessForResponse(ResponsePageSeconds)

  /** Le molle lavorano in pixel: sotto mezzo pixel il movimento e' finito. */
  const val VisibilityThresholdPx: Float = 0.5f

  /**
   * Push, pop, tap-back, conferma del dito. Criticamente smorzata: una pagina non rimbalza mai.
   *
   * Vive qui e non in `FluidMotion` per ora: e' la molla di un componente solo, e diventera' un
   * token quando la useranno anche altri.
   */
  fun page(): SpringSpec<Float> =
    spring(FluidMotion.DampingChrome, ResponsePage, VisibilityThresholdPx)

  /** Il ritorno di un back annullato: piu' corto, la pagina "torna a posto". */
  fun pageReturn(): SpringSpec<Float> =
    spring(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy, VisibilityThresholdPx)

  // --- La corsa --------------------------------------------------------------------------------

  /** Quanta corsa serve perche' la pagina sopra sia del tutto fuori. */
  fun exitTravel(transition: FluidPageTransition, width: Float): Float = when (transition) {
    FluidPageTransition.Slide -> max(0f, width)
    FluidPageTransition.ShrinkSlide, FluidPageTransition.Expand -> max(0f, width) * ShrinkExitFraction
  }

  /** La frazione d'uscita, 0 a riposo e 1 fuori. Larghezza zero: gia' fuori. */
  fun exitFraction(transition: FluidPageTransition, travel: Float, width: Float): Float {
    val exit = exitTravel(transition, width)
    if (exit <= 0f) return 1f
    return (travel / exit).coerceIn(0f, 1f)
  }

  /**
   * Il trascinamento in-app: la distanza del dito da dove si e' posato, verso l'uscita, dal
   * fotogramma in cui supera la soglia di movimento (non da li': il dito e il bordo della pagina
   * restano alla stessa distanza che avevano al contatto). [startTravel] e' la corsa che la pagina
   * aveva quando il dito l'ha ripresa a meta' di un annullo.
   */
  fun inAppTravel(fingerX: Float, downX: Float, direction: Int, startTravel: Float = 0f): Float =
    max(0f, startTravel + (fingerX - downX) * direction.sign())

  /** Il verso dell'uscita col gesto di sistema: quello del dito (dal bordo destro, a sinistra). */
  fun systemDirection(edge: FluidSwipeEdge, layoutDirection: Int): Int = when (edge) {
    FluidSwipeEdge.Left -> 1
    FluidSwipeEdge.Right -> -1
    FluidSwipeEdge.None -> layoutDirection.sign()
  }

  /**
   * Il punto da cui si misura il dito del gesto di sistema.
   *
   * Se l'host tocca il bordo da cui e' partito il gesto, l'ancora e' il bordo: la pagina salta sotto
   * il dito al primo evento (il dito era gia' a qualche pixel dal bordo quando il sistema ha
   * riconosciuto il gesto) e da li' lo segue. Se non lo tocca (un pannello di dettaglio in mezzo allo
   * schermo) l'ancora e' il primo evento, e la pagina parte da zero.
   */
  fun systemAnchor(
    firstTouchX: Float,
    edge: FluidSwipeEdge,
    hostLeft: Float,
    hostRight: Float,
    windowWidth: Float,
    tolerancePx: Float = 1f,
  ): Float = when (edge) {
    FluidSwipeEdge.Left -> if (abs(hostLeft) <= tolerancePx) hostLeft else firstTouchX
    FluidSwipeEdge.Right -> if (abs(hostRight - windowWidth) <= tolerancePx) hostRight else firstTouchX
    FluidSwipeEdge.None -> firstTouchX
  }

  /** La corsa dal gesto di sistema: il dito meno l'ancora, verso l'uscita, mai oltre la fine. */
  fun systemTravel(touchX: Float, anchor: Float, direction: Int, exitTravel: Float): Float =
    ((touchX - anchor) * direction.sign()).coerceIn(0f, max(0f, exitTravel))

  /**
   * Il sistema annulla un back con ~200 ms di ritardo: prima riporta il suo `progress` a zero con
   * un'animazione sua, a dito gia' alzato, poi chiama l'annullo. Due eventi col dito fermo e il
   * progresso in calo vogliono dire che il dito non c'e' piu': si parte subito, invece di lasciare la
   * pagina ferma per un quinto di secondo. Col dito davvero fermo il progresso non scende.
   */
  fun inferSystemCancel(
    previousTouchX: Float,
    touchX: Float,
    previousProgress: Float,
    progress: Float,
  ): Boolean = abs(touchX - previousTouchX) < SystemStillPx && progress < previousProgress

  /**
   * Il rilascio del dito: confermare o tornare.
   *
   * [velocity] e' in px/s lungo l'uscita (positiva = verso fuori). Una spinta decide da sola in
   * entrambi i versi; altrimenti conta la distanza, [CommitFraction] della larghezza con il tetto
   * [CommitCapDp]. Larghezza zero: non c'e' niente da tenere, conferma.
   */
  fun backRelease(travel: Float, velocity: Float, width: Float, density: Float): Boolean {
    val fling = FlingDpPerSecond * density
    if (velocity >= fling) return true
    if (velocity <= -fling) return false
    val threshold = min(CommitFraction * max(0f, width), CommitCapDp * density)
    return travel >= threshold
  }

  // --- Cosa si vede ----------------------------------------------------------------------------

  /**
   * La pagina sopra, in funzione della corsa.
   *
   * [opening] vale solo per [FluidPageTransition.Expand], che si apre in un modo (scala dal punto
   * toccato, ferma) e si chiude in un altro (come ShrinkSlide).
   */
  fun pageFrame(
    transition: FluidPageTransition,
    travel: Float,
    width: Float,
    opening: Boolean = false,
  ): FluidPageFrame {
    val t = max(0f, travel)
    return when {
      transition == FluidPageTransition.Slide -> FluidPageFrame(t, 1f)
      transition == FluidPageTransition.Expand && opening -> {
        val f = exitFraction(transition, t, width)
        FluidPageFrame(0f, 1f - (1f - ExpandInitialScale) * f)
      }
      else -> {
        val shrinkEnd = ShrinkTravelFraction * max(0f, width)
        val s = if (shrinkEnd <= 0f) 1f else smoothstep((t / shrinkEnd).coerceIn(0f, 1f))
        FluidPageFrame(max(0f, t - shrinkEnd), 1f - ShrinkAmount * s)
      }
    }
  }

  /**
   * La pagina sotto: −(W/3)·(1 − e), lungo l'uscita (il chiamante moltiplica per il verso).
   *
   * Arriva a zero esattamente quando quella sopra e' uscita, e il suo bordo d'uscita sta sempre oltre
   * il bordo d'ingresso di quella sopra: fra le due non si vede mai lo sfondo. Vale per una pagina
   * sopra a piena misura: vedi [belowOffset].
   */
  fun belowParallax(exitFraction: Float, width: Float): Float =
    -(max(0f, width) * BelowParallaxFraction) * (1f - exitFraction.coerceIn(0f, 1f))

  /**
   * La pagina sotto per [transition]. Con [FluidPageTransition.Slide] arretra di un terzo
   * ([belowParallax]). Quando la pagina sopra si rimpicciolisce ([FluidPageTransition.ShrinkSlide],
   * [FluidPageTransition.Expand]) sta ferma: una pagina sopra piu' piccola dello schermo lascia
   * scoperte due strisce, sopra e sotto, e una pagina sotto spostata di lato le lascerebbe vuote — lo
   * sfondo nero della finestra in alto e in basso a destra (beta 2 sull'emulatore).
   */
  fun belowOffset(transition: FluidPageTransition, exitFraction: Float, width: Float): Float = when (transition) {
    FluidPageTransition.Slide -> belowParallax(exitFraction, width)
    FluidPageTransition.ShrinkSlide, FluidPageTransition.Expand -> 0f
  }

  /** Chi e' davanti (vetro della barra, sorgente della chrome): passa di mano a meta' uscita. */
  fun pageIsFront(exitFraction: Float, isUpper: Boolean): Boolean =
    if (isUpper) exitFraction < FrontHandoff else exitFraction >= FrontHandoff

  /**
   * Il passo fra schede, invariato: la pagina che arriva sta sopra, opaca, e percorre il 13% della
   * larghezza; quella che esce va dall'altra parte dello stesso tanto e sfuma nei suoi 150 ms.
   *
   * [time] e' il tempo trascorso in frazione di `DurationPeer` (lineare), [eased] la stessa cosa
   * passata per la curva. Torna (x della pagina che arriva, x di quella che esce, alpha di quella
   * che esce), con le x gia' nel verso [direction].
   */
  fun peerFrame(time: Float, eased: Float, width: Float, direction: Int): Triple<Float, Float, Float> {
    val travel = FluidMotion.PeerSlideFraction * max(0f, width)
    val d = direction.sign().toFloat()
    val arriving = d * travel * (1f - eased.coerceIn(0f, 1f))
    val departing = -d * travel * eased.coerceIn(0f, 1f)
    val fadeTime = (time * FluidMotion.DurationPeer / FluidMotion.DurationPeerFadeOut).coerceIn(0f, 1f)
    val alpha = 1f - FluidMotion.EaseIn.transform(fadeTime)
    return Triple(arriving, departing, alpha)
  }

  /**
   * Gli angoli della carta: quelli del telefono, ma solo dove l'host tocca davvero un angolo dello
   * schermo. Un pannello di dettaglio a destra ha gli angoli del telefono a destra e retti a
   * sinistra; un host sotto una barra di sistema opaca non ne ha in alto. Senza i raggi del display
   * (sotto API 31, o un display rettangolare) la carta ha angoli retti e resta l'ombra.
   */
  fun pageCardRadii(
    hostLeft: Float,
    hostTop: Float,
    hostRight: Float,
    hostBottom: Float,
    windowWidth: Float,
    windowHeight: Float,
    display: FluidPageCorners,
    tolerancePx: Float = 1f,
  ): FluidPageCorners {
    fun near(a: Float, b: Float) = abs(a - b) <= tolerancePx
    val left = near(hostLeft, 0f)
    val top = near(hostTop, 0f)
    val right = near(hostRight, windowWidth)
    val bottom = near(hostBottom, windowHeight)
    return FluidPageCorners(
      topLeft = if (left && top) display.topLeft else 0f,
      topRight = if (right && top) display.topRight else 0f,
      bottomRight = if (right && bottom) display.bottomRight else 0f,
      bottomLeft = if (left && bottom) display.bottomLeft else 0f,
    )
  }

  // --- La pila ---------------------------------------------------------------------------------

  /**
   * Che cosa e' successo alla pila (LibrePods SwipeBack.kt:56-60: indietro se la cima c'era gia').
   *
   * I pari vincono su tutto: una scheda aperta sopra un'altra (pila `[home] → [home, cerca]`) e'
   * strutturalmente un push, ma e' un passo laterale.
   */
  fun <K> classifyStackChange(
    old: List<K>,
    new: List<K>,
    peer: ((K, K) -> Int)? = null,
  ): FluidStackChange<K> {
    val oldTop = old.lastOrNull()
    val newTop = new.lastOrNull()
    if (oldTop == null || newTop == null || oldTop == newTop) {
      return FluidStackChange(FluidStackChangeKind.None, oldTop, newTop)
    }
    val peerDirection = peer?.invoke(oldTop, newTop)?.sign() ?: 0
    if (peerDirection != 0) {
      return FluidStackChange(FluidStackChangeKind.Peer, oldTop, newTop, direction = peerDirection)
    }
    val newTopInOld = old.lastIndexOf(newTop)
    if (newTopInOld >= 0) {
      return FluidStackChange(
        FluidStackChangeKind.Pop,
        oldTop,
        newTop,
        skipped = max(0, old.size - 2 - newTopInOld),
      )
    }
    if (oldTop in new) return FluidStackChange(FluidStackChangeKind.Push, oldTop, newTop)
    return FluidStackChange(FluidStackChangeKind.Replace, oldTop, newTop)
  }

  // --- La barra ancorata ------------------------------------------------------------------------

  /** Quanto si sposta di lato un titolo agganciato mentre le pagine cambiano. */
  const val BarTitleShiftDp: Float = 16f

  /**
   * Il morph dei tasti di vetro della barra in funzione della frazione d'uscita [e] della pagina sopra:
   * fermo nel primo e nell'ultimo 10% (il dito appena posato non deve gia' deformare i tasti), poi una
   * smoothstep. 0 = i tasti della pagina sopra, 1 = quelli della pagina sotto.
   */
  fun barMorph(e: Float): Float = smoothstep(((e - 0.10f) / 0.80f).coerceIn(0f, 1f))

  /**
   * Le icone sopra i pezzi che viaggiano: quelle della pagina sopra se ne vanno presto
   * (1 − m/0,35), quelle della pagina sotto arrivano tardi ((m − 0,25)/0,5). Le due non sono mai
   * leggibili insieme, in entrambi i versi del viaggio.
   */
  fun barIconAlpha(m: Float, ofLower: Boolean): Float =
    if (ofLower) ((m - 0.25f) / 0.5f).coerceIn(0f, 1f) else 1f - (m / 0.35f).coerceIn(0f, 1f)

  /**
   * Il titolo agganciato di una delle due pagine: (alpha, traslazione x in px).
   *
   * Quello che esce sparisce nella prima meta' e scivola di [shiftPx] verso l'uscita della sua
   * pagina; quello che arriva compare dal 40% e arriva dallo stesso lato. [forward] e' un push (la
   * pagina sopra arriva), false un back (la pagina sopra se ne va). [direction] e' il verso
   * d'uscita della pagina sopra.
   */
  fun barTitleFrame(
    e: Float,
    isUpper: Boolean,
    forward: Boolean,
    shiftPx: Float,
    direction: Int,
  ): Pair<Float, Float> {
    val x = e.coerceIn(0f, 1f)
    val d = direction.sign().toFloat()
    return if (isUpper) {
      val alpha = if (forward) {
        smoothstep(((1f - x) - 0.4f) / 0.5f)
      } else {
        1f - smoothstep(x / 0.5f)
      }
      alpha to shiftPx * x * d
    } else {
      val alpha = if (forward) {
        1f - smoothstep((1f - x) / 0.5f)
      } else {
        smoothstep((x - 0.4f) / 0.5f)
      }
      alpha to -shiftPx * (1f - x) * d
    }
  }

  /**
   * Le coppie dei pezzi di vetro della barra fra due pagine, nelle coordinate della barra.
   *
   * I pezzi stanno da due parti (il tasto indietro a sinistra, le azioni a destra: la meta' della
   * barra decide) e ogni parte si accoppia dall'esterno verso l'interno: la prima azione da destra
   * con la prima da destra, il tasto indietro col tasto indietro. Chi resta senza compagno lo riceve
   * come **seme**, un pixel sul proprio centro: nasce o si spegne sul posto invece di attraversare la
   * barra. Null se le coppie superano [maxPieces] (il campo fuso ne porta sei): allora le icone si
   * incrociano e basta.
   */
  fun barPieceJourney(
    from: List<Rect>,
    to: List<Rect>,
    barWidth: Float,
    maxPieces: Int = 6,
  ): List<Pair<Rect, Rect>>? {
    val half = barWidth / 2f
    fun side(pieces: List<Rect>, left: Boolean) = pieces
      .filter { (it.center.x < half) == left }
      .sortedBy { if (left) it.center.x else -it.center.x }
    val pairs = ArrayList<Pair<Rect, Rect>>()
    for (left in listOf(true, false)) {
      val a = side(from, left)
      val b = side(to, left)
      for (i in 0 until max(a.size, b.size)) {
        val source = a.getOrNull(i)
        val target = b.getOrNull(i)
        pairs += when {
          source != null && target != null -> source to target
          source != null -> source to seedAt(source)
          else -> seedAt(target!!) to target
        }
      }
    }
    if (pairs.isEmpty() || pairs.size > maxPieces) return null
    return pairs
  }

  private fun seedAt(piece: Rect): Rect {
    val c = piece.center
    return Rect(c.x - 0.5f, c.y - 0.5f, c.x + 0.5f, c.y + 0.5f)
  }

  internal fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
  }

  private fun Int.sign(): Int = when {
    this > 0 -> 1
    this < 0 -> -1
    else -> 0
  }
}
