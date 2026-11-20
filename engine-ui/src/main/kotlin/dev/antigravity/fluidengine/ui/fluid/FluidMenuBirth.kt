package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidDropWarp
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm

/*
 * La nascita del menu dal tasto e dal gruppo col vetro liquido (sessione «contestuale», fase tasto). Alessio: «serve anche
 * la versione da tasto (o gruppo di tasti) a menu, che è esattamente quella mostrata da Apple».
 *
 * Due riferimenti di Apple, ognuno col suo (scelta dell'orchestratore del 2026-11-19):
 *  - il tasto singolo e' B1 (a), WWDC25 219 a 5:06: il tasto in basso a sinistra del telefono diventa il menu. E' piu'
 *    rapido della graffetta: pieno a ~133 ms, superamento del 3-4% a 167-200 ms, fermo a ~367 ms, voci ingrandite e a
 *    fuoco a ~267 ms. La sagoma e' un rettangolo molto arrotondato che cresce dall'angolo del tasto: nessun uovo.
 *  - il gruppo e' B1 (b), la graffetta (7:15): le icone si sfocano, la capsula si stringe in una goccia sul tasto, la goccia
 *    sale con la lente, diventa fagiolo e uovo storto con la coda verso il tasto, il corpo si allarga e si posa. E' la
 *    nascita del contestuale ([fluidContextBirthSlab], [fluidContextBirthWarp]) col seme sul tasto premuto.
 *
 * Le funzioni sono pure e in millisecondi dalla nascita: l'orologio e' di [FluidMenuJourney]. Provate da
 * `FluidMenuBirthTest`; le tracce della fase `tasto` le mettono accanto ad Apple.
 */

/** Smoothstep fra [from] e [to]: 0 prima, 1 dopo. */
private fun menuBirthStep(t: Float, from: Float, to: Float): Float {
  val x = ((t - from) / (to - from)).coerceIn(0f, 1f)
  return x * x * (3f - 2f * x)
}

/** Da [a] a [b] per [f], esatto ai capi. */
private fun menuBirthLerp(a: Float, b: Float, f: Float): Float = when {
  f <= 0f -> a
  f >= 1f -> b
  else -> a + (b - a) * f
}

/**
 * Le misure di B1 (a), nel sistema del pannello a riposo come quelle della graffetta ([FluidContextBirthProfile]): `u` lungo
 * l'asse della crescita (0 = il bordo vicino al tasto a riposo, 1 = il lontano, in frazioni dell'altezza), la larghezza in
 * frazioni del pannello. Tempi dal primo fotogramma che si muove (f9148, t = 0, 29,97 fps).
 *
 * Contorni letti su f9148-f9156 (`sessioni/f5a-2/scelte-misure/apple-tasto-misure.csv`, ±6 px) e, per la posa, sui bordi
 * di f9156-f9170 (sessione contestuale, fase tasto): il menu a riposo e' 601-932 × 318-700 px del video HD, fermo da
 * ~367 ms. Il tasto, a t = 0, e' 100 × 112 px (stimato: c'e' il dito). Cosa si vede: a 33 ms una goccia quasi quadrata al
 * 58% del menu, a 67 ms all'80%, pieno a 133 ms; il corpo **sale** del 5% sopra la posa (il bordo vicino resta sul tasto)
 * e supera del 3-4% in larghezza e in altezza a 167-200 ms, poi scende e si posa.
 */
internal object FluidButtonBirthProfile {
  private val Ms = floatArrayOf(0f, 33.4f, 66.7f, 100.1f, 133.5f, 166.8f, 200.2f, 233.6f, 266.9f, 300.3f, 333.7f, 367.0f)

  /** Il bordo vicino (quello del tasto): sta sul tasto, 5% sopra la posa, fino a 133 ms; poi scende alla posa. */
  val Near = FluidMeasuredCurve(
    Ms,
    floatArrayOf(0.045f, 0.058f, 0.052f, 0.055f, 0.052f, 0.044f, 0.030f, 0.021f, 0.009f, 0.005f, 0.003f, 0f),
  )

  /** Il bordo lontano: 0,34 e' il tasto; 1 a ~110 ms, picco 1,076 a 167 ms, fermo a 367. */
  val Far = FluidMeasuredCurve(
    Ms,
    floatArrayOf(0.338f, 0.644f, 0.866f, 0.987f, 1.063f, 1.076f, 1.064f, 1.045f, 1.022f, 1.003f, 1.003f, 1f),
  )

  /** La larghezza: il tasto 0,30, poi 0,58 a 33 ms, piena a 133, picco 1,036 a 200 ms. */
  val Width = FluidMeasuredCurve(
    Ms,
    floatArrayOf(0.302f, 0.582f, 0.802f, 0.944f, 1.008f, 1.033f, 1.036f, 1.027f, 1.021f, 1.008f, 1.002f, 1f),
  )

  /**
   * Il raggio degli angoli in frazioni del lato corto: il tasto e' tondo (0,5), a 33 ms la goccia ha gli angoli al 31%, a
   * 100 ms al 20%, il menu a riposo al 12% (letti a occhio sui contorni, ±3 px). Da [CornersFrom] il raggio va a quello del
   * pannello.
   */
  val Corner = FluidMeasuredCurve(
    Ms,
    floatArrayOf(0.5f, 0.31f, 0.25f, 0.20f, 0.17f, 0.15f, 0.14f, 0.13f, 0.12f, 0.12f, 0.12f, 0.12f),
  )
  const val CornersFrom = 200.2f
  const val CornersTo = 333.7f

  val SourceNear: Float get() = Near.at(0f)
  val SourceFar: Float get() = Far.at(0f)
  val SourceWidth: Float get() = Width.at(0f)

  /** In quanti ms la sagoma passa dal **nostro** tasto (premuto, che si scioglie) al profilo di Apple. */
  const val BlendMillis = 66.7f

  /** Fine della nascita: il menu di Apple e' fermo da 367 ms; da qui la sagoma e' il pannello. */
  const val EndMillis = 400.4f

  /** Dove il bordo lontano arriva per la prima volta al pannello: fin qui il progresso sale. */
  const val RiseEndMillis = 120f

  /**
   * Fin dove arriva l'ancora della goccia del tasto, in dp (`FluidPhysicsState.dropAnchorReachDp`; di serie
   * [FluidMotion.DropAnchorReachDp], 110). La goccia insegue la sagoma chiesta con molle rigide e smorzate sul moto della
   * sagoma vicino all'ancora, morbide e smorzate da ferme lontano: con 110 dp l'angolo lontano del menu (a 200-300 dp dal
   * tasto) restava indietro di 60-70 ms, e partendo ferma a 33 ms non poteva seguire B1 (a), che cresce del 22% del menu in
   * un fotogramma: tre giri del banco, e la simulazione degli stessi due bordi (`strumenti/anticipo_tasto.py` della
   * sessione), non scendevano sotto il 6% a 67-100 ms con nessun anticipo. Con 300 dp la goccia del tasto e' piu' tesa e
   * lo scarto massimo da Apple e' del 3%. B1 (a) e' una sagoma netta, senza coda: la goccia del gruppo resta quella di serie.
   */
  const val DropReachDp = 300f

  /**
   * L'anticipo per la goccia (vedi [FluidContextBirthProfile.FarLead]) con l'ancora di [DropReachDp]: tarato sulla
   * simulazione dei due bordi, che rifa' i tre giri del banco entro lo 0,02-0,03.
   */
  private val LeadMs = floatArrayOf(0f, 33.4f, 66.7f, 100.1f, 133.5f, 200.2f, 400.4f)
  val FarLead = FluidMeasuredCurve(LeadMs, floatArrayOf(0f, 11f, 26f, 28f, 30f, 30f, 30f))
  val WidthLead = FluidMeasuredCurve(LeadMs, floatArrayOf(0f, 12f, 28f, 29f, 29f, 30f, 30f))
  val NearLead = FluidMeasuredCurve(LeadMs, floatArrayOf(0f, 15f, 15f, 12f, 12f, 12f, 12f))

  /**
   * Quanto del superamento di Apple si chiede alla sagoma a riposo: la meta'. La goccia, anche tesa, ne aggiunge un altro 2-3%:
   * chiedendolo tutto la larghezza arrivava a 1,06 (al banco 1,062, sul Tab S9 1,061 nella prova s4h) e il bordo lontano a 1,10,
   * contro 1,036 e 1,076 di B1 (a) (S4g, NO 2: «superamento 3,5%»).
   */
  const val OvershootShare = 0.5f

  /** Le voci entrano nella goccia subito: a 33 ms in Apple si legge gia' «Wis», sfocata e ingrandita. */
  const val InkInFrom = 0f
  const val InkInTo = 33.4f
}

/**
 * La sagoma del menu dal tasto singolo a [ms] dalla nascita (B1 a), dal tasto [seed] (premuto, che si scioglie: la
 * partenza viva del viaggio) al pannello [panel] di raggio [panelRadius]. Null dalla fine in poi: e' il pannello fermo.
 *
 * Lungo l'asse della crescita i due bordi di Apple ([FluidButtonBirthProfile.Near], [FluidButtonBirthProfile.Far]); di
 * traverso ogni lato va dal bordo del tasto a quello del pannello con la stessa frazione, scelta perche' la larghezza sia
 * quella di Apple: un tasto sul margine resta fermo su quel lato e il menu cresce dall'altro, come il telefono. Nei primi
 * [FluidButtonBirthProfile.BlendMillis] la sagoma parte dal nostro tasto (a 0 ms e' esattamente lui). Gli angoli sono quelli
 * della goccia di Apple, fino al 31% del lato corto, e da 200 ms vanno al raggio del pannello.
 */
internal fun fluidButtonBirthSlab(
  seed: Rect,
  panel: Rect,
  panelRadius: Float,
  ms: Float,
  /** Con l'anticipo della goccia (vedi [fluidContextBirthSlab]); senza, la sagoma di Apple stessa. */
  leadForDrop: Boolean = true,
  /** Da quanti ms la goccia insegue questa nascita: l'anticipo cresce con lei (vedi [fluidContextBirthSlab]). */
  leadAge: Float = ms,
): FluidForm.Slab? {
  val p = FluidButtonBirthProfile
  if (ms >= p.EndMillis) return null
  if (panel.width <= 0f || panel.height <= 0f) return null
  val t = ms.coerceAtLeast(0f)
  val e = menuBirthStep(t, 0f, p.BlendMillis)
  fun over(v: Float) = if (leadForDrop && v > 1f) 1f + (v - 1f) * p.OvershootShare else v
  val age = leadAge.coerceIn(0f, t)
  val tNear = if (leadForDrop) t + p.NearLead.at(age) else t
  val tFar = if (leadForDrop) t + p.FarLead.at(age) else t
  val tWidth = if (leadForDrop) t + p.WidthLead.at(age) else t
  val down = panel.center.y >= seed.center.y
  val h = panel.height
  val nearRest = if (down) panel.top else panel.bottom
  fun u(y: Float) = if (down) (y - nearRest) / h else (nearRest - y) / h
  fun y(u: Float) = if (down) nearRest + u * h else nearRest - u * h
  val seedNear = u(if (down) seed.top else seed.bottom)
  val seedFar = u(if (down) seed.bottom else seed.top)
  val near = p.Near.at(tNear) + (seedNear - p.SourceNear) * (1f - e)
  val far = over(p.Far.at(tFar)) + (seedFar - p.SourceFar) * (1f - e)
  val a = y(near)
  val b = y(far)
  val seedW = seed.width.coerceAtMost(panel.width - 0.5f)
  val travelW = panel.width - seedW
  val widthNow = over(p.Width.at(tWidth)) * panel.width + (seedW - p.SourceWidth * panel.width) * (1f - e)
  val g = ((widthNow - seedW) / travelW).coerceAtLeast(0f)
  val left = seed.left + (panel.left - seed.left) * g
  val right = seed.right + (panel.right - seed.right) * g
  val frame = Rect(left, minOf(a, b), right, maxOf(a, b))
  val side = frame.minDimension
  // Tondo il tasto, poi gli angoli della goccia: mai oltre il 78% di mezzo lato (il ritaglio delle voci vuole lati dritti,
  // vedi [FluidContextBirthProfile.RoundnessInTravel]).
  val cap = side / 2f * menuBirthLerp(1f, FluidContextBirthProfile.RoundnessInTravel, menuBirthStep(t, 0f, 33.4f))
  val r = menuBirthLerp(p.Corner.at(t) * side, panelRadius, menuBirthStep(t, p.CornersFrom, p.CornersTo)).coerceAtMost(cap)
  return FluidForm.Slab(frame, FluidCornerRadii.all(r))
}

/**
 * Il progresso del menu dal tasto a [ms]: la corsa del bordo lontano di Apple dal tasto (0) al menu (1), col suo superamento
 * (1,11 a 167 ms). E' il numero che leggono gli altri pezzi del menu (il materiale, la lente delle voci, il punto di luce,
 * il verso della chiusura). Vale 1 **esatto** dalla fine in poi.
 */
internal fun fluidButtonBirthProgress(ms: Float): Float {
  val p = FluidButtonBirthProfile
  if (ms >= p.EndMillis) return 1f
  if (ms <= 0f) return 0f
  return ((p.Far.at(ms) - p.SourceFar) / (1f - p.SourceFar) * menuBirthStep(ms, 0f, p.BlendMillis)).coerceAtLeast(0f)
}

/** Quanto delle voci c'e' nella goccia del tasto a [ms]: entrano nei primi 33 ms, ingrandite dalla lente. */
internal fun fluidButtonBirthInk(ms: Float): Float =
  menuBirthStep(ms, FluidButtonBirthProfile.InkInFrom, FluidButtonBirthProfile.InkInTo)

/** In quanti ms la capsula del gruppo si stringe nella goccia sul tasto (B1 b: a 33 ms al 63%, a 67 ms la goccia). */
internal const val FluidGroupGatherMillis = 66.7f

/** La goccia sul tasto premuto del gruppo: tonda, del lato corto del tasto, sul suo centro. */
internal fun fluidGroupBirthDrop(trigger: Rect): Rect {
  val r = trigger.minDimension / 2f
  val c = trigger.center
  return Rect(c.x - r, c.y - r, c.x + r, c.y + r)
}

private fun lerpRadii(a: FluidCornerRadii, b: FluidCornerRadii, f: Float) = FluidCornerRadii(
  topLeft = menuBirthLerp(a.topLeft, b.topLeft, f),
  topRight = menuBirthLerp(a.topRight, b.topRight, f),
  bottomRight = menuBirthLerp(a.bottomRight, b.bottomRight, f),
  bottomLeft = menuBirthLerp(a.bottomLeft, b.bottomLeft, f),
)

/**
 * La sagoma del menu dal gruppo a [ms] dalla nascita (B1 b, la graffetta): la capsula [capsule] si stringe nella goccia sul
 * tasto premuto [trigger] in [FluidGroupGatherMillis], e da li' e' la nascita del contestuale ([fluidContextBirthSlab]) col
 * seme sul tasto: la goccia sale, si stacca e torna, il corpo si allarga verso il pannello [panel]. Null dalla fine in poi.
 */
internal fun fluidGroupBirthSlab(
  capsule: Rect,
  trigger: Rect,
  panel: Rect,
  panelRadius: Float,
  ms: Float,
  leadForDrop: Boolean = true,
  leadAge: Float = ms,
): FluidForm.Slab? {
  val birth = fluidContextBirthSlab(fluidGroupBirthDrop(trigger), panel, panelRadius, ms, leadForDrop, leadAge) ?: return null
  val gather = menuBirthStep(ms, 0f, FluidGroupGatherMillis)
  if (gather >= 1f) return birth
  val from = FluidCornerRadii.all(capsule.minDimension / 2f)
  return FluidForm.Slab(lerp(capsule, birth.frame, gather), lerpRadii(from, birth.cornerRadii, gather))
}

/**
 * Il contorno del menu dal gruppo a [ms] ([fluidContextBirthWarp] col seme sul tasto): la goccia con la lente, il fagiolo,
 * l'uovo storto con la coda verso il tasto. Mentre la capsula si raccoglie la forma non la piega (i termini della sagoma
 * crescono con la raccolta), la lente si': la goccia ingrandisce cio' che ha sotto da subito, come in Apple.
 */
internal fun fluidGroupBirthWarp(trigger: Rect, panel: Rect, slab: FluidForm.Slab, ms: Float): FluidDropWarp? {
  val warp = fluidContextBirthWarp(fluidGroupBirthDrop(trigger), panel, slab, ms) ?: return null
  val gather = menuBirthStep(ms, 0f, FluidGroupGatherMillis)
  if (gather >= 1f) return warp
  return warp.copy(
    tailTaper = warp.tailTaper * gather,
    headTaper = warp.headTaper * gather,
    waist = warp.waist * gather,
    belly = warp.belly * gather,
  )
}

/**
 * Il contorno di un menu dal gruppo chiuso a meta' nascita: quello dell'istante del congedo [frozenMs], sull'asse della
 * sagoma di adesso [slab], che si spegne mentre il ritorno lascia la sagoma fermata ([FluidMotion.MenuReturnHold]: [q] e'
 * la frazione del ritorno che manca, 1 al congedo). Null quando e' spento: e' la sagoma del ritorno.
 */
internal fun fluidGroupBirthWarpFading(trigger: Rect, panel: Rect, slab: FluidForm.Slab, frozenMs: Float, q: Float): FluidDropWarp? {
  val f = menuBirthStep(q, 1f - FluidMotion.MenuReturnHold, 1f)
  if (f <= 0f) return null
  val warp = fluidGroupBirthWarp(trigger, panel, slab, frozenMs) ?: return null
  if (f >= 1f) return warp
  return warp.copy(
    tailTaper = warp.tailTaper * f,
    headTaper = warp.headTaper * f,
    waist = warp.waist * f,
    belly = warp.belly * f,
    lens = warp.lens * f,
    magnify = warp.magnify * f,
    clarity = warp.clarity * f,
  )
}

/**
 * Quanto della faccia del tasto (o delle icone del gruppo) e' gia' partito a [ms] dalla nascita, nella scala del progresso
 * che leggono [fluidMenuFaceAlpha] e la barra (`groupProgress`): la faccia del tasto e' spenta a ~29 ms, le icone del gruppo
 * sfocate al massimo a ~41 ms e via a ~54 ms. In Apple a 33 ms nella goccia del tasto ci sono gia' le voci, e le icone della
 * capsula sono sfocate dal primo fotogramma e sparite a 67 ms.
 */
internal fun fluidMenuBirthFaceProgress(ms: Float): Float = (ms / FluidMenuBirthFaceMillis).coerceIn(0f, 1f)

internal const val FluidMenuBirthFaceMillis = 83f

/** La fine della nascita: il tasto (B1 a) o il gruppo (B1 b). */
internal fun fluidMenuBirthEndMillis(group: Boolean): Float =
  if (group) FluidContextBirthProfile.EndMillis else FluidButtonBirthProfile.EndMillis

/** Il progresso del viaggio a [ms] dalla nascita, per il tasto o per il gruppo. */
internal fun fluidMenuBirthProgress(group: Boolean, ms: Float): Float =
  if (group) fluidContextBirthProgress(ms) else fluidButtonBirthProgress(ms)

/** Le voci nella goccia a [ms]: subito nel tasto (B1 a), da 67 a 117 ms nel gruppo (B1 b). */
internal fun fluidMenuBirthInk(group: Boolean, ms: Float): Float =
  if (group) fluidContextBirthInk(ms) else fluidButtonBirthInk(ms)

/**
 * L'istante della nascita a cui corrisponde [progress], per una nascita ripresa a meta' (un menu riaperto mentre si
 * chiudeva): il primo istante della salita in cui il progresso vale [progress].
 */
internal fun fluidMenuBirthMillisAt(group: Boolean, progress: Float): Float {
  if (group) return fluidContextBirthMillisAt(progress)
  if (progress <= 0f) return 0f
  var lo = 0f
  var hi = FluidButtonBirthProfile.RiseEndMillis
  if (fluidButtonBirthProgress(hi) <= progress) return hi
  repeat(24) {
    val mid = (lo + hi) / 2f
    if (fluidButtonBirthProgress(mid) < progress) lo = mid else hi = mid
  }
  return hi
}
