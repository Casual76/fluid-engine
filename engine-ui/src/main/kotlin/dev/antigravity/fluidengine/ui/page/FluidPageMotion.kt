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
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

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

  /** Il dito del gesto di sistema e' fermo se non si allontana di tanto dal punto in cui si e' fermato. */
  const val SystemStillPx: Float = 0.5f

  /** Quanti eventi di fila, col dito fermo, il progresso deve scendere prima di dire "annullato" (3.0.4). */
  const val SystemCancelFalls: Int = 2

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

  /**
   * La risposta della molla dello zoom di UIKit, in secondi (4.0): WWDC24 10145, l'iPad della dimostrazione (iOS 18).
   * Quel video e' **a meta' velocita'** (Apple lo dice nella trascrizione: «all the animations in this video are slowed
   * down to half speed»), quindi un suo fotogramma a 29,97 fps vale 16,7 ms di tempo vero. A tempo vero l'apertura da
   * una cella toccata, da ferma, e' una molla critica di risposta 0,355 s (scarto quadratico 0,002 su 24 fotogrammi, i
   * lati della sagoma); il ritorno nella cella dopo il dito, partito con la velocita' del rilascio, sta fra 0,305 e
   * 0,37 s. F5B.3 l'aveva letta a 0,71 s, senza la nota sulla velocita': lo zoom andava lento il doppio. Misura e fit:
   * `sessioni/corr-zoom/strumenti/misura_10145_tempo_vero.py`. E' appena piu' morbida di [ResponsePageSeconds].
   */
  const val ResponseZoomSeconds: Double = 0.355
  val ResponseZoom: Float = FluidMotion.stiffnessForResponse(ResponseZoomSeconds)

  /**
   * Lo zoom dalla sorgente, nei due versi (push, pop, conferma del dito, le inversioni), solo quando una sagoma viaggia
   * fra la sorgente e la pagina: la molla misurata su Apple ([ResponseZoomSeconds]), critica, come in UIKit, dove
   * apertura e ritorno sono la stessa transizione. Push, pop e conferma di `Slide`, `ShrinkSlide` e dell'`Expand` senza
   * sorgente restano su [page], al bit.
   */
  fun zoom(): SpringSpec<Float> =
    spring(FluidMotion.DampingChrome, ResponseZoom, VisibilityThresholdPx)

  /** F6B.5b, a occhio, scelta di Alessio del 21 novembre 2026. La faccia lascia il centro al 30%,
   * i comandi salgono dopo il 70%. Lo scambio usa le curve di blur e opacità misurate in R7. */
  internal const val SourceFaceEnd = 0.30f
  internal const val PageChromeStart = 0.70f
  internal const val PageChromeRiseDp = 12f

  /** Cresce, a occhio, scelta di Alessio del 21 novembre 2026. Molla critica rapida al tocco,
   * poi solidificazione a geometria ferma. Il ritorno scioglie il materiale nel primo 8% del gesto. */
  internal val growTap = spring<Float>(FluidMotion.DampingChrome, FluidMotion.stiffnessForResponse(0.245), VisibilityThresholdPx)
  internal const val GrowSolidMillis = 125
  internal const val GrowMeltStart = 0.92f

  /** Angoli all’arrivo, a occhio, scelta di Alessio del 21 novembre 2026. Cresce usa solo la seconda
   * metà della solidificazione. Zoom e Sale dedicano lo stesso tempo al solo bordo. */
  internal const val GrowCornerStart = 0.5f
  internal const val ZoomCornerMillis = GrowSolidMillis / 2

  /** F6D.0 §2–3, Note WWDC25 284 (f22252, f22611–23, f23472–76). Il 12% a piena copertura
   * è estrapolato, fiducia media. Per ShrinkSlide ed Expand senza sorgente è un'analogia Fluid.
   * In scuro e all'annullo manca una misura Apple: stessa curva per scelta Fluid. */
  internal const val SlideDim = 0.12f

  /** Ombra di Slide. F6D.0 §2, Note WWDC25 284, f22230–52: circa 1–3 punti presso il
   * bordo e meno di un punto a 0,03 W. Elevazione e tinta sono una taratura Fluid per HWUI.
   * Fiducia media in chiaro; tinta nera anche in scuro per scelta Fluid (Apple NV). */
  internal const val SlideElevationFraction = 0.035f
  /** Taratura Fluid del nero di HWUI sui fotogrammi f22230–52 di Note (F6D.0 §2).
   * Fiducia media sull’ombra chiara, scelta Fluid in scuro dove Apple resta NV. */
  internal const val SlideShadowAlpha = 0.36f

  /** F6D.0 §4, WWDC24 10145, iPadOS 18 f966 e f3183 (12,5% e 16,5%). Il massimo
   * di zoom è una stima Fluid, fiducia media. Cresce conserva il suo 18% per analogia,
   * fiducia bassa. Scuro, annullo e parità con iOS 26/27: NV. */
  internal const val GrowDim = 0.18f

  /** F6D.0 §7, iPadOS 18 f3183/86/89/90. Approssimazione Fluid della coda al ritorno,
   * fiducia media nel tratto osservabile. Un gesto ripreso continua sulla stessa curva. */
  private const val ZoomReturnDimExponent = 0.55f

  /** Intensità sulla crescita effettiva della sagoma (0 alla sorgente, 1 a pagina intera). */
  internal fun zoomDim(growth: Float, returning: Boolean): Float {
    val q = growth.coerceIn(0f, 1f)
    return GrowDim * if (returning) q.pow(ZoomReturnDimExponent) else q
  }

  /** Sfocatura massima di Cresce, scelta Fluid del 21 novembre 2026. */
  internal const val GrowBlurDp = 10f

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
   * un'animazione sua, a dito gia' alzato, poi chiama l'annullo. Per non lasciare la pagina ferma un
   * decimo di secondo e piu' l'annullo si deduce prima: il progresso e' sceso [falls] eventi di fila
   * (almeno [SystemCancelFalls]) col dito fermo **nel punto piu' lontano del gesto** ([reach], il
   * `touchX` nel verso dell'uscita, non sotto [farthest] di oltre [SystemStillPx]).
   *
   * **Il punto piu' lontano, perche' il sistema porta il progresso a zero anche col dito giu' (3.0.4).**
   * Misurato sull'emulatore (API 37): quando il dito torna verso il bordo il sistema decide che il back
   * non partira' e riporta il suo `progress` a zero con la sua molla, a dito fermo e ancora giu' (da 0,53
   * a 0 in sei fotogrammi, `touchX` immobile). E' lo stesso disegno di un annullo, e fino alla 3.0.3 un
   * evento cosi' bastava: la pagina partiva verso casa sotto un dito fermo, e il primo movimento la
   * rimetteva sotto il dito — lo scatto «come indecisa su dove sia il dito», col dito fermo dopo un
   * piccolo ritorno e mentre lo si riporta indietro. Lo stesso disegno, piu' corto, lo fa la molla che
   * smussa il `progress` dopo un passo indietro. Nel punto piu' lontano invece il progresso puo' solo
   * salire, finche' il dito e' giu': se scende, il dito si e' alzato. Altrove la pagina resta sotto il
   * dito e aspetta la decisione del sistema (l'annullo arriva quando la sua molla ha finito).
   */
  fun inferSystemCancel(falls: Int, reach: Float, farthest: Float): Boolean =
    falls >= SystemCancelFalls && reach >= farthest - SystemStillPx

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
   * Se in questo fotogramma il vetro dei tasti lo porta la superficie che viaggia (F1B.2): solo mentre la forma si
   * muove, cioè con [m] (il [barMorph] di adesso) **strettamente** fra i due capi.
   *
   * Ai capi la forma è ferma ed è, pezzo per pezzo, quella dei tasti di una delle due pagine (a 0 quelli della
   * pagina sopra, a 1 quelli della pagina sotto, i semi di chi nasce o muore ridotti a un pixel): lì il vetro lo
   * disegnano i tasti, ognuno il suo. Così il passaggio di consegne cade quando la forma si ferma (nel push a
   * `e = 0,10`, circa 210 ms dopo la partenza) e non quando la molla della pagina scende sotto mezzo pixel (circa
   * 570 ms), e alla partenza la superficie prende il vetro solo quando la forma comincia a muoversi.
   *
   * [barMorph] è una `smoothstep` su un argomento tenuto in [0, 1]: ai capi vale 0 e 1 esatti, quindi qui il
   * confronto è esatto e non serve una soglia.
   */
  fun barJourneyCarries(m: Float): Boolean = m > 0f && m < 1f

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

  // --- La barra disegnata dal modello (F4A.3) ----------------------------------------------------------------------

  /**
   * Il passo del titolo in linea che cambia fra due pagine (R7 26 e A9): **al più 8 dp**, nel verso della navigazione.
   * Il titolo che arriva viene dal lato da cui arriva la sua pagina, quello che se ne va va dall'altra parte; nessun passo
   * in un cambio di scheda (D4: come un cambio di stato). Il resto del cambio è la sfocatura dei tasti (A1), sul tempo
   * suo, non sul progresso della pagina. Il vecchio passo legato alla corsa ([BarTitleShiftDp]) resta per le pagine con le
   * `actions` di sempre.
   */
  const val BarTitleStepDp: Float = 8f

  /**
   * Il passo del titolo che **arriva**, in frazioni di [BarTitleStepDp] col segno del lato da cui arriva: 1 finché non
   * si vede (prima di [GlassContentDefaults.EnterAt], dove la sua opacità è zero), poi una S fino a 0 alla fine del
   * cambio. [u] è la frazione del tempo del cambio dello strato (quella di [GlassContentJourney]).
   */
  fun barTitleEnterStep(u: Float): Float = 1f - smoothstep((u - GlassContentDefaults.EnterAt) / (1f - GlassContentDefaults.EnterAt))

  /**
   * Il passo del titolo che **se ne va**: da dove era ([from], in frazioni del passo: zero a riposo, di più se era ancora
   * in arrivo) fino a un passo intero dall'altra parte, con una S che finisce quando l'icona che esce è sparita
   * ([GlassContentDefaults.ExitEnd]). [elapsed] è il tempo dalla sua uscita in frazioni del tempo del cambio.
   */
  fun barTitleExitStep(elapsed: Float, from: Float): Float =
    from + (-1f - from) * smoothstep(elapsed / GlassContentDefaults.ExitEnd)

  /**
   * Il lato da cui arriva il titolo della pagina nuova, col segno dello schermo: in un push dal lato da cui entra la
   * pagina ([direction], il verso d'uscita della pagina sopra), in un pop o nel gesto indietro dall'altro, in un cambio
   * di scheda e a riposo nessuno (0).
   */
  fun barTitleStepSign(kind: FluidPageMove, direction: Int): Int = when (kind) {
    FluidPageMove.Push -> direction.sign()
    FluidPageMove.Pop, FluidPageMove.Back -> -direction.sign()
    FluidPageMove.Peer, FluidPageMove.None -> 0
  }

  /**
   * Le coppie dei vetri della barra dichiarata fra due pagine **per identità** ([from] e [to]: l'identità del vetro, il
   * suo riquadro), per la sola variante del gesto indietro che segue il dito (l'interruttore delle prove,
   * [FluidPageBarDiagnostics.gestureFollowsFinger]): i vetri con la stessa identità si accoppiano dovunque stiano (niente
   * accoppiamento per lato e ordine), chi c'è da una parte sola nasce o si spegne da un seme sul proprio centro come nella
   * barra di prima. Null senza pezzi o oltre [maxPieces] (quanti ne porta un campo fuso di Fluid-physics): allora anche
   * questa variante aspetta la conferma. Di serie la barra non passa da qui: la trasforma il piano flottante.
   */
  fun barModelPieceJourney(
    from: Map<Any, Rect>,
    to: Map<Any, Rect>,
    maxPieces: Int = BarModelMaxPieces,
  ): List<Pair<Rect, Rect>>? {
    val pairs = ArrayList<Pair<Rect, Rect>>()
    for ((id, rect) in from) pairs += rect to (to[id] ?: seedAt(rect))
    for ((id, rect) in to) if (id !in from) pairs += seedAt(rect) to rect
    if (pairs.isEmpty() || pairs.size > maxPieces) return null
    return pairs
  }

  /** Quanti pezzi porta la superficie della variante che segue il dito: un gruppo di Fluid-physics ne ha al più otto. */
  const val BarModelMaxPieces: Int = 8

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
