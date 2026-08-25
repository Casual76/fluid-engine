package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape

/**
 * I quattro raggi di uno [FluidForm.Slab], in pixel assoluti.
 *
 * Assoluti e non in `Dp` di proposito: una forma è geometria di fase di disegno, lo stesso contratto
 * di `Shape.createOutline`, e il morphing interpola questi numeri decine di volte al secondo — una
 * conversione di densità per raggio per fotogramma sarebbe solo un posto in più in cui sbagliare lo
 * spazio. Chi costruisce una forma converte una volta, con la densità che ha già in mano.
 */
@Immutable
data class FluidCornerRadii(
  val topLeft: Float,
  val topRight: Float,
  val bottomRight: Float,
  val bottomLeft: Float,
) {
  companion object {
    val Zero = FluidCornerRadii(0f, 0f, 0f, 0f)

    fun all(radius: Float): FluidCornerRadii = FluidCornerRadii(radius, radius, radius, radius)
  }
}

/**
 * Una silhouette che Fluid-physics sa tenere ferma o far viaggiare verso un'altra.
 *
 * È il vocabolario dell'intero sottosistema: qualsiasi cosa il motore trasformi — un cerchio in una
 * stella, due tasti in un pannello — prima diventa uno di questi tre casi. La geometria è in pixel
 * nello spazio della superficie che la ospita, perché è lì che vive tutto quello che la consuma: lo
 * shader della lente, il path della tinta, l'outline del bordo speculare.
 *
 * Il contratto di movimento sta in [FluidPhysicsState]: una forma da sola è solo una fotografia.
 */
@Immutable
sealed interface FluidForm {

  /** Il rettangolo che la silhouette occupa dentro la superficie. */
  val frame: Rect

  /**
   * Famiglia A: tutto quello che un campo di distanza a raggi d'angolo sa dire — rettangolo,
   * quadrato, cerchio, capsula, angolo continuo.
   *
   * È la famiglia di lavoro: interpolare due Slab è un lerp di otto numeri, la rifrazione resta
   * piena per tutto il viaggio, e copre da sola il caso pratico che giustifica il motore (un tasto
   * che diventa un menu).
   */
  @Immutable
  data class Slab(
    override val frame: Rect,
    val cornerRadii: FluidCornerRadii = FluidCornerRadii.Zero,
    /**
     * Raccordo continuo alla maniera della casa. Onorato **a riposo**, dove l'occhio ha il tempo di
     * leggere la curvatura; durante il viaggio la sagoma passa per angoli circolari, che è
     * l'eccezione già documentata in `RouteMotion` — un angolo che cambia a ogni fotogramma non può
     * permettersi un path generico, e la differenza fra le due curve sta dentro il pixel finché
     * qualcosa si muove.
     */
    val smoothing: Float = ContinuousCornerShape.IosSmoothing,
  ) : FluidForm

  /**
   * Famiglia B: una sagoma chiusa qualsiasi — un trapezio, una stella, uno scarabocchio disegnato
   * col dito.
   *
   * [vertices] sono i vertici di *controllo* del poligono (non il campionamento di resa),
   * normalizzati 0..1 dentro [frame], in senso orario nello spazio dello schermo. Il raccordo è
   * uniforme perché è quello che il campo di distanza sa arrotondare a costo zero; la fedeltà per
   * vertice, dove serve, si ottiene aggiungendo vertici.
   */
  @Immutable
  data class Poly(
    override val frame: Rect,
    val vertices: List<Offset>,
    /** Raccordo uniforme dei vertici, in pixel. */
    val rounding: Float = 0f,
  ) : FluidForm {
    init {
      require(vertices.size >= 3) { "Un poligono ha almeno tre vertici, non ${vertices.size}." }
      require(vertices.size <= FluidFormPresets.MaxVertices) {
        "Al massimo ${FluidFormPresets.MaxVertices} vertici: oltre, lo shader non li sa portare."
      }
    }
  }

  /**
   * Fino a [MaxPieces] silhouette rese come **una** superficie liquida: pezzi vicini si versano
   * l'uno nell'altro con un ponte di materiale, ed è l'effetto-firma di due tasti che si fondono in
   * un pannello.
   *
   * I pezzi sono [Slab] o [Poly], mai altri gruppi: un gruppo è una disposizione locale, non un
   * albero. Il ponte esiste solo al tier [FluidPhysicsTier.Full], dove il campo di distanza dei
   * pezzi si fonde con uno smooth minimum; sotto, i pezzi restano geometria separata che si
   * sovrappone — più povero, mai rotto.
   */
  @Immutable
  data class Group(
    val pieces: List<FluidForm>,
    /**
     * Raggio di fusione in pixel: da quanto lontano due pezzi cominciano a versarsi l'uno
     * nell'altro.
     */
    val blendRadius: Float = 48f,
  ) : FluidForm {

    init {
      require(pieces.isNotEmpty()) { "Un gruppo vuoto non è una forma." }
      require(pieces.size <= MaxPieces) {
        "Al massimo $MaxPieces pezzi: ogni pezzo è un termine del campo di distanza per pixel."
      }
      require(pieces.none { it is Group }) { "Un gruppo non contiene gruppi: è una disposizione, non un albero." }
      require(pieces.all { it is Slab }) {
        "I pezzi di un gruppo sono Slab: il campo di distanza fuso dello shader di gruppo parla " +
          "solo rettangoli arrotondati. Una sagoma libera viaggia da sola."
      }
    }

    override val frame: Rect = run {
      var union = pieces.first().frame
      for (index in 1 until pieces.size) {
        val piece = pieces[index].frame
        union = Rect(
          minOf(union.left, piece.left),
          minOf(union.top, piece.top),
          maxOf(union.right, piece.right),
          maxOf(union.bottom, piece.bottom),
        )
      }
      union
    }

    companion object {
      /**
       * Sei, perché il costo dello shader di gruppo è `pezzi × pixel` e sei rettangoli coprono ogni
       * composizione che una schermata vera ha mai chiesto (una coppia di tasti, una pila di righe).
       */
      const val MaxPieces: Int = 6
    }
  }

  companion object {

    /** Un cerchio perfetto: la forma di riposo del Playground. */
    fun circle(center: Offset, radius: Float): Slab {
      val r = radius.coerceAtLeast(0f)
      return Slab(
        frame = Rect(center.x - r, center.y - r, center.x + r, center.y + r),
        cornerRadii = FluidCornerRadii.all(r),
        // A raggio pieno il raccordo continuo degenera comunque nell'arco puro; dichiararlo evita
        // di lasciare la decisione a chi costruisce l'anello.
        smoothing = 0f,
      )
    }

    /**
     * Legge una [CornerBasedShape] — comprese le forme di [dev.antigravity.fluidengine.ui.fluid
     * .FluidRadius] — in uno [Slab] posato su [frame], così un componente entra nel motore con la
     * silhouette che aveva già.
     */
    fun slabOf(
      shape: CornerBasedShape,
      frame: Rect,
      density: Density,
      layoutDirection: LayoutDirection,
    ): Slab {
      val size = Size(frame.width, frame.height)
      val half = size.minDimension / 2f
      val ltr = layoutDirection == LayoutDirection.Ltr
      fun resolve(corner: androidx.compose.foundation.shape.CornerSize): Float =
        corner.toPx(size, density).coerceIn(0f, half)
      return Slab(
        frame = frame,
        cornerRadii = FluidCornerRadii(
          topLeft = resolve(if (ltr) shape.topStart else shape.topEnd),
          topRight = resolve(if (ltr) shape.topEnd else shape.topStart),
          bottomRight = resolve(if (ltr) shape.bottomEnd else shape.bottomStart),
          bottomLeft = resolve(if (ltr) shape.bottomStart else shape.bottomEnd),
        ),
        smoothing = (shape as? ContinuousCornerShape)?.let { ContinuousCornerShape.IosSmoothing }
          ?: 0f,
      )
    }
  }
}
