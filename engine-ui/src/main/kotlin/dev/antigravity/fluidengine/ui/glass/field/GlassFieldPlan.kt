/*
 * Il PIANO del campo: la geometria che corpo, luce e ombra di una superficie `Liquid` ricevono, cioè i pezzi
 * (`pieceRect`, `pieceRadii`, `pieceBlend`, `pieceSmooth` del nucleo, `GlassFieldKernel.kt`), e la regola
 * **forma → piano** (piano 3.7) che li ricava dalla `Shape` di una lastra ferma.
 *
 * È il punto in cui si decide quale curva ha l'angolo del vetro, ed è uno solo: lo leggono l'innesto
 * (`GlassFieldEffects.kt`: corpo, luce, ombra, ritaglio hardware) e lo scrittore delle scene del banco
 * (`test/.../trace/GlassScenes.kt`). Ciò che si misura e si sceglie su una tavola ha così la stessa sagoma di
 * ciò che l'engine disegna.
 *
 * Nasce in F1A.6 con le sole lastre ferme. F1B.1 lo generalizza (anello, riquadri, deformazione, fino a otto
 * pezzi in viaggio): è `PhysicsRenderPlan` di Fluid-physics portato qui.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import kotlin.math.max
import kotlin.math.min

/**
 * Il ritaglio hardware di una lastra raccordata, in frazione del raggio nominale.
 *
 * Il raccordo del campo esce dal cerchio nominale fino allo 0,44% del raggio attorno alla diagonale
 * (`GlassFieldSlabBlock`): un ritaglio al raggio pieno lo taglierebbe lì, e il filo, che è largo due o tre
 * pixel, uscirebbe più sottile proprio in mezzo all'angolo. Al 98% il contorno del campo non esce mai dal
 * ritaglio (a 0,6 di `smoothing`, ricalcolato per F1A.6 su 20 000 punti dell'angolo: sulla diagonale restano
 * 0,39% del raggio di margine) e la sagoma la taglia la copertura dello shader, che è la sola a conoscerla.
 * Decisione dell'orchestratore su F1A.1 (2026-11-11). Resta un rettangolo arrotondato: il ritaglio è ancora
 * quello hardware (invariante 18).
 */
internal const val GlassSmoothedClipScale: Float = 0.98f

/**
 * Come il campo vede **una lastra ferma**: i quattro raggi, il raccordo, e a quale frazione del raggio
 * nominale sta il ritaglio hardware.
 *
 * I raggi sono nell'unità in cui sono arrivati a [glassSlabGeometry] (pixel dello shader per il corpo, pixel
 * del nodo per luce e ombra, dp per le scene). È un contenitore che chi chiama riusa.
 */
internal class GlassSlabGeometry {
  var topLeft: Float = 0f
  var topRight: Float = 0f
  var bottomRight: Float = 0f
  var bottomLeft: Float = 0f

  /** `pieceSmooth`: lo `smoothing` *chiesto*; la riduzione che impone il lato corto la fa il campo. */
  var smoothing: Float = 0f

  /** 1, o [GlassSmoothedClipScale] se l'angolo è raccordato. */
  var clipScale: Float = 1f

  /** Vero se almeno un angolo è raccordato: allora il ritaglio hardware non sta al raggio nominale. */
  val smoothed: Boolean get() = clipScale != 1f

  /** Vero se i raggi sono quelli chiesti (limitati a mezzo lato corto), non quelli equivalenti dei piccoli. */
  var nominal: Boolean = true

  /**
   * Vero se il campo disegna **esattamente** il contorno che la forma disegna a questa taglia. Da F1B.1 lo è sempre.
   *
   * Fino al giro di correzione di F1A il nucleo sceglieva l'angolo per quadrante e ne limitava il raggio a mezzo lato
   * corto: un angolo che scende oltre la metà del lato (`RoundedCornerShape(topStart = 80.dp, …)` su un lato di 100,
   * che Compose disegna a 80) non lo sapeva rendere, qui usciva falso e la superficie restava `Classic`: un fumetto di
   * chat `22/22/22/6` aveva il vetro di prima su una riga e il nuovo su due. Ora `glassSlab` ha il ramo largo
   * ([GlassFieldSlabBlock]) e i raggi escono di qui **come Compose li disegna**, senza limite a mezzo lato. Il campo
   * resta per chi lo legge (`glassFieldFits`, i test): non c'è più una forma ad angoli tondi che lo metta a falso.
   */
  var fits: Boolean = true

  /** Vero se almeno un angolo passa mezzo lato corto: la lastra prende il ramo largo del nucleo. */
  var wide: Boolean = false

  fun set(topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float): GlassSlabGeometry {
    this.topLeft = topLeft
    this.topRight = topRight
    this.bottomRight = bottomRight
    this.bottomLeft = bottomLeft
    return this
  }
}

/**
 * Vero se una forma lunga [maxSidePx] **pixel dello schermo** è un «piccolo»: l'engine la disegna già come
 * rettangolo arrotondato al raggio equivalente (`ContinuousCornerShape.createOutline`), e il campo fa lo
 * stesso. La soglia è in pixel veri, non in pixel della cattura né in dp: è quella di chi disegna la forma.
 */
internal fun glassSlabIsSmall(maxSidePx: Float): Boolean = maxSidePx < ContinuousCornerShape.SmallShapeCutoffPx

/**
 * Di quanto un raggio può passare mezzo lato corto ed essere ancora «a mezzo lato»: la normalizzazione delle coppie
 * (`minDimension / somma`, poi per il raggio) arriva a mezzo lato con l'errore di un `float`, e una capsula non deve
 * diventare una forma che il campo non sa rendere per un milionesimo.
 */
private const val GlassFieldFitTolerance: Float = 1e-4f

/**
 * La regola **forma → piano** per una lastra (piano 3.7), in numeri: dai raggi chiesti a quelli che il campo
 * riceve.
 *
 * Prima di tutto i raggi diventano **quelli che Compose disegna** (F1A, giro di correzione): `CornerBasedShape`
 * riduce in proporzione le due coppie sullo stesso fianco (alto-sinistra con basso-sinistra, alto-destra con
 * basso-destra) quando la loro somma supera il **lato corto**, e non tocca un raggio solo. Poi, per forma:
 *
 *  - **angoli circolari** ([continuous] falso: un rettangolo, una `RoundedCornerShape`): il rettangolo arrotondato
 *    con quei raggi, ridotti tutti insieme se due raggi accanto superano un lato (come lo riduce Skia, e
 *    `RoundRect.contains`). Nessun raccordo. Un raggio può restare oltre mezzo lato corto (l'angolo accanto gli
 *    lascia il posto): esce com'è, [GlassSlabGeometry.wide] lo dice, e il nucleo lo rende col ramo largo (F1B.1);
 *  - **capsula** (ogni raggio da mezzo lato corto in su): un semicerchio esatto. Raggi a mezzo lato corto,
 *    nessun raccordo, ritaglio pieno. Nel campo il raccordo si spegnerebbe da solo; qui si scrive zero perché
 *    resti zero anche in ciò che si legge;
 *  - **piccoli** ([small]): l'engine li disegna come rettangoli arrotondati al raggio equivalente
 *    (`ContinuousCornerShape.roundedEquivalent`: per 0,82, tranne l'angolo che è già a mezzo lato, che resta
 *    com'è) e ritaglia con gli stessi raggi (`ShapeProvider.fastClipShape`). Il campo usa quei raggi, anche oltre
 *    mezzo lato, come sopra;
 *  - **grandi**: i raggi, limitati a mezzo lato corto come li limita l'emettitore dell'engine
 *    (`continuousCornerPoints`), lo `smoothing` della forma, e il ritaglio hardware a [GlassSmoothedClipScale].
 *    Senza un raggio da arrotondare non c'è raccordo e il ritaglio resta pieno. Il campo li rende sempre.
 *
 * I conti sono quelli di Compose nello stesso ordine e in `float`, perché i casi al limite (una coppia che arriva
 * esattamente a mezzo lato, il confronto `>= half` dei piccoli) cadano dalla stessa parte.
 *
 * L'unità è quella degli argomenti, e deve essere una sola: [width], [height] e i raggi insieme.
 *
 * @param smoothing lo `smoothing` della forma (`ContinuousCornerShape.smoothing`); non conta se [continuous]
 *   è falso.
 * @param small [glassSlabIsSmall] sul lato lungo in pixel dello schermo. Non conta se [continuous] è falso.
 */
internal fun glassSlabGeometry(
  width: Float,
  height: Float,
  topLeft: Float,
  topRight: Float,
  bottomRight: Float,
  bottomLeft: Float,
  smoothing: Float,
  continuous: Boolean,
  small: Boolean,
  out: GlassSlabGeometry = GlassSlabGeometry(),
): GlassSlabGeometry {
  val minDimension = min(width, height).let { if (it.isFinite() && it > 0f) it else 0f }
  val half = minDimension / 2f
  fun finite(radius: Float): Float = if (radius.isFinite()) max(radius, 0f) else 0f
  fun capped(radius: Float): Float = if (radius.isFinite()) radius.coerceIn(0f, half) else 0f
  out.smoothing = 0f
  out.clipScale = 1f
  out.nominal = true
  out.fits = true
  out.wide = false

  // Le coppie sullo stesso fianco, come `CornerBasedShape.createOutline`: contro il lato corto, non contro il fianco.
  var tl = finite(topLeft)
  var tr = finite(topRight)
  var br = finite(bottomRight)
  var bl = finite(bottomLeft)
  if (tl + bl > minDimension) {
    val scale = minDimension / (tl + bl)
    tl *= scale
    bl *= scale
  }
  if (tr + br > minDimension) {
    val scale = minDimension / (tr + br)
    tr *= scale
    br *= scale
  }

  if (!continuous || half <= 0f) {
    roundRectRadii(width, height, tl, tr, br, bl, half, out)
    return out
  }

  val atHalf = half * (1f - GlassFieldFitTolerance)
  val capsule = tl >= atHalf && tr >= atHalf && br >= atHalf && bl >= atHalf
  if (capsule) {
    out.set(half, half, half, half)
    return out
  }
  if (small) {
    // La stessa scelta, angolo per angolo, di `ContinuousCornerShape.roundedEquivalent`: un angolo già a mezzo lato
    // resta com'è, anche oltre, e il rettangolo arrotondato che ne esce lo riduce come sopra.
    fun equivalent(radius: Float): Float =
      if (radius >= half) radius else radius * ContinuousCornerShape.RoundedEquivalentFactor
    roundRectRadii(width, height, equivalent(tl), equivalent(tr), equivalent(br), equivalent(bl), half, out)
    out.nominal = false
    return out
  }
  // L'emettitore dell'engine limita ogni angolo a mezzo lato corto: il contorno è quello, e il campo lo rende.
  out.set(capped(tl), capped(tr), capped(br), capped(bl))
  val s = if (smoothing.isFinite()) smoothing.coerceIn(0f, 1f) else 0f
  val rounded = out.topLeft > 0f || out.topRight > 0f || out.bottomRight > 0f || out.bottomLeft > 0f
  if (s > 0f && rounded) {
    out.smoothing = s
    out.clipScale = GlassSmoothedClipScale
  }
  return out
}

/**
 * I raggi di un rettangolo arrotondato come lo disegna la piattaforma: se due raggi accanto superano il lato, tutti
 * e quattro si riducono dello stesso fattore, il più piccolo dei quattro lati (`RoundRect.scaledRadiiRect` di
 * Compose, `SkRRect` di Skia). [out] li riceve così, anche oltre mezzo lato corto: il nucleo li rende col ramo
 * largo, e [GlassSlabGeometry.wide] dice quando.
 */
private fun roundRectRadii(
  width: Float,
  height: Float,
  topLeft: Float,
  topRight: Float,
  bottomRight: Float,
  bottomLeft: Float,
  half: Float,
  out: GlassSlabGeometry,
) {
  var scale = 1f
  fun side(a: Float, b: Float, length: Float) {
    val sum = a + b
    if (sum > length && sum != 0f) scale = min(scale, length / sum)
  }
  side(bottomLeft, topLeft, height)
  side(topLeft, topRight, width)
  side(topRight, bottomRight, height)
  side(bottomRight, bottomLeft, width)
  val tl = topLeft * scale
  val tr = topRight * scale
  val br = bottomRight * scale
  val bl = bottomLeft * scale
  // Entro la tolleranza un raggio «a mezzo lato» resta a mezzo lato: una capsula non prende il ramo largo per
  // l'errore di un prodotto.
  val limit = half * (1f + GlassFieldFitTolerance)
  fun settled(radius: Float): Float = if (radius > half && radius <= limit) half else max(radius, 0f)
  out.set(settled(tl), settled(tr), settled(br), settled(bl))
  out.wide = tl > limit || tr > limit || br > limit || bl > limit
}

/**
 * Vero se il campo rende [shape] **a ogni taglia**, e chi la disegna non deve misurarla per sapere se resta `Liquid`.
 *
 * Da F1B.1 vale per ogni forma che il campo sa rendere ([glassFieldRenders]): il ramo largo del nucleo rende anche
 * l'angolo oltre mezzo lato corto, e un fumetto di chat `22/22/22/6` è `Liquid` a 40 dp come a 44. Prima valeva solo
 * per i quattro angoli uguali, e le altre forme si misuravano al layout (`GlassFieldFit`): un nodo in più, e un
 * fotogramma `Liquid` prima di passare a `Classic`.
 */
internal fun glassFieldFitsAtAnySize(shape: Shape): Boolean = glassFieldRenders(shape)

/**
 * Vero se il campo disegna il contorno di [shape] a questa taglia ([GlassSlabGeometry.fits]). Da F1B.1 è
 * [glassFieldRenders] a ogni taglia; la domanda resta, con la taglia, per chi la fa già e per i test.
 */
internal fun glassFieldFits(shape: Shape, size: Size, density: Density, layoutDirection: LayoutDirection): Boolean {
  if (!glassFieldRenders(shape)) return false
  if (glassFieldFitsAtAnySize(shape)) return true
  if (!size.isSpecified || !(size.width > 0f) || !(size.height > 0f)) return true
  return glassSlabGeometry(shape, size, density, layoutDirection, size.maxDimension, GlassSlabGeometry()).fits
}

/**
 * Vero se il campo sa rendere [shape]: un rettangolo, o una forma ad angoli **tondi** (`RoundedCornerShape`,
 * `AbsoluteRoundedCornerShape`, `CircleShape`, e le `ContinuousCornerShape` dell'engine, capsula compresa).
 * Ogni altra resta `Classic`.
 *
 * Non basta essere una `CornerBasedShape`: anche `CutCornerShape` lo è, e il campo, che conosce solo angoli
 * circolari o raccordati, la disegnava con gli angoli tondi (divergenza trovata in F1A.10). Una forma a
 * spigoli tagliati non ha un raggio da dare al campo: resta al vetro di prima, che la ritaglia col suo path.
 * Le sottoclassi di `CornerBasedShape` scritte da un'app restano `Classic` per la stessa ragione: il campo non
 * sa che curva disegnano.
 */
internal fun glassFieldRenders(shape: Shape): Boolean =
  shape === RectangleShape ||
    shape is RoundedCornerShape ||
    shape is AbsoluteRoundedCornerShape ||
    shape is ContinuousCornerShape

/**
 * [glassSlabGeometry] letta da una `Shape`: la regola **forma → piano** come la chiede chi disegna.
 *
 *  - `RectangleShape`: raggi zero;
 *  - `ContinuousCornerShape` (le forme dell'engine, capsula compresa): i quattro raggi alla taglia del nodo,
 *    col suo `smoothing`;
 *  - `RoundedCornerShape` e `AbsoluteRoundedCornerShape` (`CircleShape` compresa): i quattro raggi, angoli
 *    circolari;
 *  - `FluidConcentricShape` (F3.5) si risolverà al layout in quattro raggi e rientrerà nel secondo caso.
 *
 * Una forma che [glassFieldRenders] rifiuta non arriva qui (resta `Classic`); se arrivasse, è un rettangolo.
 *
 * @param size la taglia della lastra, nell'unità di [density]: i raggi escono in quei pixel.
 * @param maxSidePx il lato lungo in **pixel dello schermo** (uguale a `size.maxDimension` se [size] non è
 *   quella rimpicciolita di una cattura): decide i «piccoli».
 */
internal fun glassSlabGeometry(
  shape: Shape,
  size: Size,
  density: Density,
  layoutDirection: LayoutDirection,
  maxSidePx: Float,
  out: GlassSlabGeometry = GlassSlabGeometry(),
): GlassSlabGeometry {
  if (shape !is CornerBasedShape) {
    return glassSlabGeometry(size.width, size.height, 0f, 0f, 0f, 0f, 0f, continuous = false, small = false, out = out)
  }
  val topStart = shape.topStart.toPx(size, density)
  val topEnd = shape.topEnd.toPx(size, density)
  val bottomEnd = shape.bottomEnd.toPx(size, density)
  val bottomStart = shape.bottomStart.toPx(size, density)
  // Una forma «assoluta» non si specchia con la direzione di lettura, come nella lente di oggi.
  val rtl = layoutDirection == LayoutDirection.Rtl && shape !is AbsoluteRoundedCornerShape
  val continuous = shape as? ContinuousCornerShape
  return glassSlabGeometry(
    width = size.width,
    height = size.height,
    topLeft = if (rtl) topEnd else topStart,
    topRight = if (rtl) topStart else topEnd,
    bottomRight = if (rtl) bottomStart else bottomEnd,
    bottomLeft = if (rtl) bottomEnd else bottomStart,
    smoothing = continuous?.smoothing ?: 0f,
    continuous = continuous != null,
    small = glassSlabIsSmall(maxSidePx),
    out = out,
  )
}

/**
 * I pezzi di un campo, nella forma degli uniform del nucleo. Riusato da chi lo riempie: nessuna allocazione
 * per fotogramma.
 *
 * In F1A.6 lo riempie soltanto [setSlab] (una lastra ferma grande quanto il nodo), con [pieces] a uno: è la
 * taglia degli array della variante a un pezzo (`GlassFieldKernel1`), e uno uniform ad array vuole
 * esattamente i numeri che dichiara. F1B.1 lo usa a [GlassFieldMaxPieces] per grappoli e viaggi, senza
 * cambiare chi lo manda allo shader.
 *
 * @param pieces quante caselle hanno gli array: 1 per i programmi a una lastra, [GlassFieldMaxPieces] per
 *   quelli a otto.
 */
internal class GlassFieldPlan(val pieces: Int = GlassFieldMaxPieces) {

  /** Quanti pezzi sono vivi (`pieceCount`). */
  var count: Int = 0
    private set

  /** `pieceRect`: per pezzo centro x, centro y, mezza larghezza, mezza altezza. */
  val rects: FloatArray = FloatArray(pieces * GlassFieldGeometry.FloatsPerPiece)

  /** `pieceRadii`: per pezzo alto-sinistra, alto-destra, basso-destra, basso-sinistra. */
  val radii: FloatArray = FloatArray(pieces * GlassFieldGeometry.FloatsPerPiece)

  /** `pieceBlend`: il raggio di fusione col già fuso. Zero a riposo. */
  val blend: FloatArray = FloatArray(pieces)

  /** `pieceSmooth`: lo `smoothing` chiesto per pezzo. */
  val smooth: FloatArray = FloatArray(pieces)

  /** Una lastra sola, grande [width] x [height], con l'angolo in alto a sinistra nell'origine. */
  fun setSlab(width: Float, height: Float, geometry: GlassSlabGeometry): GlassFieldPlan {
    count = 1
    rects[0] = width / 2f
    rects[1] = height / 2f
    rects[2] = width / 2f
    rects[3] = height / 2f
    radii[0] = geometry.topLeft
    radii[1] = geometry.topRight
    radii[2] = geometry.bottomRight
    radii[3] = geometry.bottomLeft
    blend[0] = 0f
    smooth[0] = geometry.smoothing
    return this
  }

  /** Svuota il piano: nessun pezzo vivo, caselle a zero (uno uniform ad array si manda sempre per intero). */
  fun clear(): GlassFieldPlan {
    count = 0
    rects.fill(0f)
    radii.fill(0f)
    blend.fill(0f)
    smooth.fill(0f)
    return this
  }

  /**
   * Aggiunge un pezzo (F1B.1: i gruppi e i viaggi di Fluid-physics). Oltre [pieces] non fa niente, come lo shader
   * che non lo leggerebbe.
   *
   * @param blend il raggio di fusione col già fuso; la casella del primo pezzo non si legge.
   */
  fun add(
    centerX: Float,
    centerY: Float,
    halfWidth: Float,
    halfHeight: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
    blend: Float,
    smoothing: Float,
  ): GlassFieldPlan {
    if (count >= pieces) return this
    val at = count * GlassFieldGeometry.FloatsPerPiece
    rects[at] = centerX
    rects[at + 1] = centerY
    rects[at + 2] = halfWidth
    rects[at + 3] = halfHeight
    radii[at] = topLeft
    radii[at + 1] = topRight
    radii[at + 2] = bottomRight
    radii[at + 3] = bottomLeft
    this.blend[count] = blend
    smooth[count] = smoothing
    count++
    return this
  }
}
