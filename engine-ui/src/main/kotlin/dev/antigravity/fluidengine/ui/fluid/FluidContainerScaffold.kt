package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.isRenderEffectSupported
import dev.antigravity.fluidengine.ui.theme.FluidListGroup

/**
 * Opaque, bounded destination for row-to-detail navigation. On a screen with an ambient canvas the
 * group that holds the content is a pane of glass (the material of `FluidListGroup(glass = true)`),
 * on any other it is the solid container it has always been.
 *
 * Its secondary block used to be held back behind a fixed 170 ms delay so it would not compete with
 * a shared-element transform. Nothing shares layers with this screen any more, and the delay was
 * only ever visible as the defect it caused: content that arrived after the page had already
 * settled. The page is now complete on its first frame.
 */
@Composable
fun FluidContainerScaffold(
  title: String,
  onBack: (() -> Unit)?,
  modifier: Modifier = Modifier,
  hero: @Composable ColumnScope.() -> Unit,
  secondary: @Composable ColumnScope.() -> Unit = {},
  /**
   * Il fondale della sezione da cui si arriva. Senza, la pagina di dettaglio era l'unica grigia
   * in un'app in cui ogni pagina ha la sua lavata di colore: aprire un compito era come uscire
   * dall'app per un momento.
   */
  ambient: FluidAmbient? = null,
) {
  FluidScreen(
    title = title,
    modifier = modifier,
    onBack = onBack,
    ambient = ambient,
  ) {
    item(key = "container-detail") {
      // Su un fondale ambientale il gruppo e' una lastra di contenuto, come `FluidListGroup(glass = true)`
      // (3.0, fase 9): un pannello solo per il gruppo, mai uno per riga, e il testo che ci sta sopra
      // resta opaco. Senza canvas — una pagina piatta, un foglio, sotto API 31 — resta il contenitore
      // pieno di sempre, colore compreso: `FluidListGroup` ripiegherebbe su un altro grigio.
      val onGlass = LocalFluidCanvasBackdrop.current != null && isRenderEffectSupported()
      val body: @Composable ColumnScope.() -> Unit = {
        hero()
        secondary()
      }
      if (onGlass) {
        FluidListGroup(glass = true) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            content = body,
          )
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(FluidRadius.Group))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp),
          content = body,
        )
      }
    }
  }
}
