package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Ritaglia il contenuto sulla silhouette VIVA di [state].
 *
 * E' il pezzo che permette a un'interfaccia DIVERSA di arrivare dentro una superficie che si
 * trasforma. [fluidPhysicsContent] risolve il caso in cui il contenuto sta comodo dentro il proprio
 * frame d'arrivo: si dissolve, zooma di qualche punto e non sborda. Quando il traguardo e' la
 * finestra intera quel patto non basta — un contenuto impaginato a schermo pieno e posato li' esce
 * dalla sagoma per tutta la prima meta' del viaggio, e al tier Full il clip hardware resta il
 * rettangolo del nodo per contratto (vedi `FluidPhysicsSilhouetteShape`). Questo lo riporta dentro.
 *
 * NON e' `@Composable` e legge in fase di DISEGNO, come tutto il resto del sottosistema: il
 * progresso non entra mai in composizione.
 *
 * [active] esiste perche' a riposo un clip su un nodo a schermo intero e' lavoro per niente: quando
 * la sagoma e' gia' il traguardo, spegnerlo toglie l'outline dal layer. Va tenuto acceso per tutto
 * cio' che non e' riposo, e spento ai due capi.
 *
 * Da usare **sul contenuto**, mai sulla superficie: la superficie ha gia' il suo patto col clip.
 */
fun Modifier.fluidPhysicsClip(
  state: FluidPhysicsState,
  active: () -> Boolean = { true },
): Modifier = graphicsLayer {
  if (active()) {
    shape = state.clipShapeNow()
    clip = true
  } else {
    clip = false
  }
}
