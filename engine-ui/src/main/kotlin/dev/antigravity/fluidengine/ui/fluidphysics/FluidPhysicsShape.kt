package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Il patto fra Fluid-physics e il clip hardware del renderer.
 *
 * Una sagoma che cambia a ogni fotogramma non può chiedere al layer di ri-clipparsi dietro di lei:
 * un clip animato è o un re-clip per fotogramma (la ragione per cui il design system vietava il
 * morphing) o, se generico, una mascheratura offscreen dell'intero nodo. Quindi la superficie
 * fisica consegna al layer **sempre lo stesso rettangolo** — i bounds del nodo — e la silhouette
 * vera la ritaglia lo shader con la propria alpha. `ShapeProvider.fastClipShape` riconosce questa
 * interfaccia e usa [fastClipOutline] al posto della lettura per raggi d'angolo.
 */
internal interface GlassClipGeometry {
  fun fastClipOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline
}

/**
 * La sagoma di una superficie fisica in un certo istante.
 *
 * È un'istantanea: durante un morph lo stato ne produce una nuova a ogni cambio di progresso
 * (l'identità diversa è ciò che invalida la cache dell'`Outline` esattamente quando deve), e a
 * riposo ne tiene una sola stabile (l'identità uguale è ciò che la fa riusare). Il [path] è
 * condiviso con il disegno della tinta e del bordo speculare, così maschera, pellicola e rim
 * raccontano per costruzione la stessa silhouette.
 *
 * [clipToBounds] distingue i due mondi: al tier Full la silhouette la ritaglia l'alpha dello
 * shader e il clip hardware resta il rettangolo del nodo; al tier Balanced non c'è nessuno shader
 * a mascherare, quindi il clip deve tornare a essere la sagoma esatta — più caro, su un morph
 * transiente, e comunque l'unica cosa onesta che un dispositivo senza AGSL può fare.
 */
/**
 * La sagoma di un clip di layer, in un certo istante.
 *
 * Diversa da [FluidPhysicsSilhouetteShape] per un motivo solo, ed e' lo stesso motivo per cui
 * quella esiste: [FluidPhysicsSilhouetteShape] serve la SUPERFICIE, dove la silhouette la scolpisce
 * l'alpha dello shader e il clip del layer deve restare il rettangolo del nodo. Questa serve il
 * CONTENUTO, che un clip vero ce l'ha da avere — e glielo da' nella forma piu' economica che la
 * geometria di quel fotogramma consente: sul viaggio di uno slab solo (che e' ogni morph da un
 * controllo alla pagina) l'`Outline.Rounded` a raggi uguali prende il clip hardware del RenderNode;
 * per un gruppo o una sagoma libera si torna al path, che fa mascherare il nodo fuori schermo a
 * ogni fotogramma ed e' l'unica cosa onesta che quella geometria consente.
 */
internal class FluidPhysicsClipShape(
  private val path: Path,
  private val roundRect: RoundRect?,
) : Shape {
  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    roundRect?.let { Outline.Rounded(it) } ?: Outline.Generic(path)
}

internal class FluidPhysicsSilhouetteShape(
  val path: Path,
  private val clipToBounds: Boolean = true,
) : Shape, GlassClipGeometry {

  override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    Outline.Generic(path)

  override fun fastClipOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
    if (clipToBounds) Outline.Rectangle(Rect(Offset.Zero, size)) else Outline.Generic(path)
}
