package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop

// F6B.16: la tavola del vetro tinto. Gli elementi che l'engine riempiva col colore del tema, uno accanto all'altro e su tre
// fondi (la pagina, una foto chiara, una foto scura): la pillola scelta, il tasto in evidenza (con etichetta e tondo), il
// pulsante pieno, in alto la principale della barra e in basso il campo «Messaggio» col disco di «Invia». Il gettone sta
// nella scena `gettoni`. La tinta è quella di serie, la C della tavola, scelta da Alessio il 23 novembre 2026.

/** I tag della scena, per le prove e le riprese. */
internal object TintedGlassTags {
  const val Page = "vetro-tinto:pagina"
  const val Pill = "vetro-tinto:pillola"
  const val Prominent = "vetro-tinto:evidenza"
  const val ProminentIcon = "vetro-tinto:evidenza-tondo"
  const val Filled = "vetro-tinto:pieno"

  fun row(ground: TintedGlassGround): String = "vetro-tinto:${ground.key}"
}

/** I fondi della tavola. */
internal enum class TintedGlassGround(val key: String, val title: String, val photo: Int?) {
  Page("pagina", "Sulla pagina", null),
  LightPhoto("foto-chiara", "Sopra una foto chiara", R.drawable.viewer_flower),
  DarkPhoto("foto-scura", "Sopra una foto scura", R.drawable.viewer_astronaut),
}

/** La tavola del vetro tinto (F6B.16). */
@Composable
internal fun TintedGlassScene(onBack: () -> Unit) {
  var text by rememberSaveable { mutableStateOf("Ci vediamo alle 9") }
  FluidScreen(
    title = "Vetro tinto",
    subtitle = "F6B.16 · la tinta di serie",
    onBack = onBack,
    modifier = Modifier.testTag(TintedGlassTags.Page),
    toolbar = { primary("fatto", FluidIcons.Check, "Fatto", onClick = {}) },
    search = FluidSearchSpec.message(text, { text = it }, onSend = { text = "" }),
  ) {
    TintedGlassGround.entries.forEach { ground ->
      item(key = ground.key) { TintedGlassRow(ground) }
    }
  }
}

@Composable
private fun TintedGlassRow(ground: TintedGlassGround) {
  val backdrop = rememberGlassBackdrop()
  Column(Modifier.testTag(TintedGlassTags.row(ground))) {
    FluidSectionHeader(title = ground.title)
    Box(
      Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(ContinuousCornerShape(FluidRadius.Card)),
    ) {
      val photo = ground.photo
      if (photo != null) {
        Image(
          painter = painterResource(photo),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize().glassBackdropSource(backdrop),
        )
      }
      CompositionLocalProvider(LocalGlassBackdrop provides (if (photo != null) backdrop else LocalGlassBackdrop.current)) {
        Column(
          Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 12.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FluidChip("Marta", selected = true, onClick = {}, modifier = Modifier.testTag("${TintedGlassTags.Pill}:${ground.key}"))
            FluidChip("Luca", selected = false, onClick = {})
            FluidChip("Sara", selected = false, onClick = {})
          }
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            FluidGlassButton(
              "Fatto", onClick = {}, style = FluidGlassButtonStyle.Prominent,
              modifier = Modifier.testTag("${TintedGlassTags.Prominent}:${ground.key}"),
            )
            FluidGlassIconButton(
              onClick = {}, style = FluidGlassButtonStyle.Prominent,
              modifier = Modifier.testTag("${TintedGlassTags.ProminentIcon}:${ground.key}"),
            ) { FluidIcon(FluidIcons.Check, "Conferma") }
            FluidButton("Salva", onClick = {}, modifier = Modifier.testTag("${TintedGlassTags.Filled}:${ground.key}"))
          }
        }
      }
    }
    Box(Modifier.height(8.dp))
  }
}
