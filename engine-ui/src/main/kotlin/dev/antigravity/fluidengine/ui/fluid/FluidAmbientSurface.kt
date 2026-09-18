package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/**
 * Un fondale solo, sotto tutta la finestra.
 *
 * Serve a una cosa sola, e si vede appena manca: una finestra a piu' pannelli in cui ogni pannello
 * dipinge il proprio fondale si legge come tre telefoni appoggiati uno accanto all'altro, con due
 * cuciture verticali nel mezzo e tre colori che non c'entrano niente fra loro. Qui il fondale e' uno,
 * continuo da bordo a bordo, e i pannelli ci stanno **sopra**: la pagina e' una sola, divisa in
 * colonne, che e' quello che un'app da tablet e'.
 *
 * Funziona perche' il canvas viene registrato in un layer suo e offerto al sottoalbero come
 * [LocalFluidCanvasBackdrop]: ogni [FluidScreen] dentro lo trova, smette di dipingere il proprio
 * fondo opaco, e lo combina con la registrazione del proprio corpo. Il vetro della chrome continua
 * cosi' a rifrangere un'immagine opaca — fondale piu' contenuto — che e' la condizione che il
 * materiale ha sempre avuto.
 *
 * Passare `ambient = null` tiene il comportamento di prima: ogni schermata si dipinge il suo.
 */
@Composable
fun FluidAmbientSurface(
  ambient: FluidAmbient?,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  if (ambient == null) {
    Box(modifier = modifier.fillMaxSize()) { content() }
    return
  }
  val canvas = rememberGlassBackdrop()
  Box(modifier = modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().glassBackdropSource(canvas)) {
      FluidAmbientCanvas(ambient)
    }
    CompositionLocalProvider(LocalFluidCanvasBackdrop provides canvas) { content() }
  }
}
