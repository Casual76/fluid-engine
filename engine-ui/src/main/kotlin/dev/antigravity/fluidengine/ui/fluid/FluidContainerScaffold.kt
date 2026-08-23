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

/**
 * Opaque, bounded destination for row-to-detail navigation.
 *
 * Its secondary block used to be held back behind a fixed 170 ms delay so it would not compete with
 * a shared-element transform. Nothing shares layers with this screen any more, and the delay was
 * only ever visible as the defect it caused: content that arrived after the page had already
 * settled. The page is now complete on its first frame.
 */
@Composable
fun FluidContainerScaffold(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  hero: @Composable ColumnScope.() -> Unit,
  secondary: @Composable ColumnScope.() -> Unit = {},
) {
  FluidScreen(
    title = title,
    modifier = modifier,
    onBack = onBack,
  ) {
    item(key = "container-detail") {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(ContinuousCornerShape(FluidRadius.Group))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh)
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        hero()
        secondary()
      }
    }
  }
}
