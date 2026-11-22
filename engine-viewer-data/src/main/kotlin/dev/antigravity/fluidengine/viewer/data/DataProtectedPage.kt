package dev.antigravity.fluidengine.viewer.data

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Lock
import dev.antigravity.fluidengine.ui.fluid.icons.MenuBook
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerCustomScope

/** Un libro con DRM: quello che si sa di lui senza leggerlo, il motivo e le azioni dell'app. */
internal class DataProtectedModel(
  val title: String,
  val subtitle: String,
  val reason: String,
  val cover: ImageBitmap?,
  val openWith: (() -> Unit)?,
  val share: (() -> Unit)?,
)

/**
 * F6C.13 · Un EPUB protetto da DRM, come lo sconosciuto di D · Mix ma con il libro: la copertina (se non è cifrata) o
 * l'icona del libro con l'alone, il titolo e «autore · Libro EPUB · dimensione», l'**avviso ambra «Protetto da DRM»**
 * con il sistema e il motivo, e «Apri con…» e «Condividi» dell'app. Il fondo ha la tinta leggera, perché qui non c'è
 * niente da leggere (§5.1). Come gli archivi cifrati: il file si riconosce, il contenuto no.
 */
@Composable
internal fun DataProtectedPage(scope: FluidViewerCustomScope, model: DataProtectedModel, settings: FluidDataSettings) {
  val strings = settings.strings
  DataInner(scope) {
    val scheme = MaterialTheme.colorScheme
    val solid = dataSolid()
    // La tinta ambra come un alone dall'alto (la tinta dello sconosciuto di D), non un velo pieno; sparisce con «Riduci
    // trasparenza» e col contrasto alto.
    val tint = if (solid) Modifier.background(scheme.surface) else Modifier.background(scheme.surface)
      .background(Brush.verticalGradient(0f to DataProfile.Amber.copy(alpha = DataProfile.CardTint), 0.6f to Color.Transparent))
    Box(Modifier.fillMaxSize().then(tint), contentAlignment = Alignment.TopCenter) {
      Column(
        Modifier.widthIn(max = DataProfile.NoticeColumn).fillMaxWidth().verticalScroll(rememberScrollState())
          .padding(contentPadding).padding(horizontal = FluidScreenDefaults.HorizontalPadding, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
      ) {
        val cover = model.cover
        val shape = ContinuousCornerShape(FluidRadius.Card)
        if (cover != null) {
          Image(
            cover, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(width = 150.dp, height = 220.dp)
              .then(if (solid) Modifier else Modifier.shadow(24.dp, shape, ambientColor = DataProfile.Amber, spotColor = DataProfile.Amber))
              .clip(shape),
          )
        } else {
          Box(
            Modifier.size(DataProfile.NoticeIcon)
              .then(if (solid) Modifier else Modifier.shadow(24.dp, shape, ambientColor = DataProfile.Amber, spotColor = DataProfile.Amber))
              .background(scheme.surfaceContainerHighest, shape),
            contentAlignment = Alignment.Center,
          ) {
            FluidIcon(FluidIcons.MenuBook, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(56.dp))
          }
        }
        Text(
          model.title, Modifier.semantics { heading() }, textAlign = TextAlign.Center,
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        Text(model.subtitle, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        // L'avviso ambra di D · Mix (lo stesso colore dell'avviso dell'eseguibile di F6C.9).
        Row(
          Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh, ContinuousCornerShape(FluidRadius.Card))
            .background(DataProfile.Amber.copy(alpha = if (solid) 0f else 0.14f), ContinuousCornerShape(FluidRadius.Card))
            .padding(16.dp).semantics(mergeDescendants = true) {},
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          FluidIcon(FluidIcons.Lock, contentDescription = null, tint = DataProfile.Amber, modifier = Modifier.size(24.dp))
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(strings.protectedTitle, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(model.reason, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
          }
        }
        model.openWith?.let { FluidButton(strings.openWith, it, fillWidth = true) }
        model.share?.let { FluidButton(strings.share, it, style = FluidButtonStyle.Tinted, fillWidth = true) }
      }
    }
  }
}
