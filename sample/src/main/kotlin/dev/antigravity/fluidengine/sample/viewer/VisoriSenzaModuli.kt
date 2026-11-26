package dev.antigravity.fluidengine.sample.viewer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.remember
import dev.antigravity.fluidengine.viewer.FluidViewerExtensions
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry

// F6C.15 · La scena «Senza moduli»: gli stessi file delle famiglie, come li vede un'app che ha solo engine-viewer.
//
// La galleria include i moduli di famiglia, che il registro trova da sé con ServiceLoader. Per la scena li toglie dal
// registro (`FluidViewerRegistry.uninstall`) finché la scena è in composizione, e li rimette uscendo
// (`FluidViewerRegistry.install`, le stesse istanze). Senza famiglie il risolutore fa esattamente il giro di F6C.4:
// lo dice il KDoc di `FluidViewerFile.open`, e lo provano i test di engine-viewer. Che nel dex di un'app senza modulo non
// resti niente lo prova invece l'app di prova di F6C.8 (`:engine-viewer-prova-app`, sapore `senzaFamiglia`).

/** Toglie le famiglie dal registro finché qualcuno della scena le tiene tolte; conta, perché lista e visore si sovrappongono. */
internal object VisoriSenzaModuli {
  private var holders = 0
  private var saved: List<FluidViewerFamily> = emptyList()
  /** F8.17: anche le estensioni («Leggi testo»), che un'app con il solo engine-viewer non ha. */
  private var savedExtensions: List<String> = emptyList()

  /** Vero mentre la scena tiene il registro senza famiglie. */
  val active: Boolean get() = holders > 0

  fun enter() {
    if (holders++ == 0) {
      saved = FluidViewerRegistry.families
      FluidViewerRegistry.uninstall(*saved.map { it.id }.toTypedArray())
      savedExtensions = FluidViewerExtensions.extensions.map { it.id }
      FluidViewerExtensions.uninstall(*savedExtensions.toTypedArray())
    }
  }

  fun leave() {
    if (holders == 0) return
    if (--holders == 0) {
      val back = saved
      saved = emptyList()
      if (back.isNotEmpty()) FluidViewerRegistry.install(*back.toTypedArray())
      FluidViewerExtensions.restore(*savedExtensions.toTypedArray())
      savedExtensions = emptyList()
    }
  }
}

/**
 * Tiene il registro senza famiglie mentre questo punto è in composizione. Il primo [enter] avviene durante la
 * composizione, prima che i file della scena chiedano il loro tipo.
 */
@Composable
internal fun HoldWithoutFamilies() {
  remember { VisoriSenzaModuliHold() }
}

private class VisoriSenzaModuliHold : RememberObserver {
  private var held = false

  init {
    VisoriSenzaModuli.enter()
    held = true
  }

  private fun release() {
    if (held) {
      held = false
      VisoriSenzaModuli.leave()
    }
  }

  override fun onRemembered() {}
  override fun onForgotten() = release()
  override fun onAbandoned() = release()
}
