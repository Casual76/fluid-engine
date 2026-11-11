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
 * La regola **forma → piano** per una lastra (piano 3.7), in numeri: dai raggi chiesti a quelli che il campo
 * riceve.
 *
 *  - **angoli circolari** ([continuous] falso: un rettangolo, una `RoundedCornerShape`): i raggi chiesti,
 *    limitati a mezzo lato corto, e nessun raccordo;
 *  - **capsula** (ogni raggio da mezzo lato corto in su): un semicerchio esatto. Raggi a mezzo lato corto,
 *    nessun raccordo, ritaglio pieno. Nel campo il raccordo si spegnerebbe da solo; qui si scrive zero perché
 *    resti zero anche in ciò che si legge;
 *  - **piccoli** ([small]): l'engine li disegna come rettangoli arrotondati al raggio equivalente
 *    (`ContinuousCornerShape.roundedEquivalent`: per 0,82, tranne l'angolo che è già a mezzo lato) e ritaglia
 *    con gli stessi raggi (`ShapeProvider.fastClipShape`). Il campo usa quei raggi: niente cambia;
 *  - **grandi**: i raggi chiesti, lo `smoothing` della forma, e il ritaglio hardware a
 *    [GlassSmoothedClipScale]. Senza un raggio da arrotondare non c'è raccordo e il ritaglio resta pieno.
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
  val half = max(min(width, height), 0f) / 2f
  fun capped(radius: Float): Float = if (radius.isFinite()) radius.coerceIn(0f, half) else 0f
  out.smoothing = 0f
  out.clipScale = 1f
  out.nominal = true
  out.set(capped(topLeft), capped(topRight), capped(bottomRight), capped(bottomLeft))
  if (!continuous || half <= 0f) return out

  val capsule = topLeft >= half && topRight >= half && bottomRight >= half && bottomLeft >= half
  if (capsule) return out
  if (small) {
    // La stessa scelta, angolo per angolo, di `ContinuousCornerShape.roundedEquivalent`.
    fun equivalent(radius: Float): Float =
      if (radius >= half) half else capped(radius * ContinuousCornerShape.RoundedEquivalentFactor)
    out.set(equivalent(topLeft), equivalent(topRight), equivalent(bottomRight), equivalent(bottomLeft))
    out.nominal = false
    return out
  }
  val s = if (smoothing.isFinite()) smoothing.coerceIn(0f, 1f) else 0f
  val rounded = out.topLeft > 0f || out.topRight > 0f || out.bottomRight > 0f || out.bottomLeft > 0f
  if (s > 0f && rounded) {
    out.smoothing = s
    out.clipScale = GlassSmoothedClipScale
  }
  return out
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
}
