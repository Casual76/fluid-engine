package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import dev.antigravity.fluidengine.ui.theme.FluidLoading
import androidx.compose.ui.unit.dp

/**
 * Header above a grouped list.
 *
 * Quiet on purpose: a section header labels the group below it, so it is set in the secondary
 * colour rather than the accent. Accent-coloured headers compete with the content they introduce and
 * are the fastest way to make a settings screen look busy.
 */
@Composable
fun FluidSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  detail: String? = null,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = title.uppercase(),
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    detail?.takeIf { it.isNotBlank() }?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/**
 * Placeholder while a screen's first load is in flight.
 *
 * Sized rather than centred in the whole viewport: it lives inside the scroll, under the title, so
 * the header stays put and the screen does not jump when the content finally arrives.
 */
@Composable
fun FluidLoadingBlock(
  modifier: Modifier = Modifier,
  minHeight: Dp = 180.dp,
) {
  Box(
    modifier = modifier.fillMaxWidth().heightIn(min = minHeight),
    contentAlignment = Alignment.Center,
  ) {
    FluidLoading()
  }
}

/** Footnote under a grouped list, for the explanation a row's subtitle would make too long. */
@Composable
fun FluidSectionFootnote(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    modifier = modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}
