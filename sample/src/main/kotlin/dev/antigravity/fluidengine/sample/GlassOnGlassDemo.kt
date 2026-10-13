package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSlider
import dev.antigravity.fluidengine.ui.fluid.FluidSwitch
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.glassContainer
import dev.antigravity.fluidengine.ui.fluid.glassMaterialSource
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop

/**
 * Vetro su vetro, sopra qualcosa di nitido.
 *
 * Un palco con righe dure e testo, registrato, e sopra un pannello di vetro con dentro i controlli
 * che un'app ci mette davvero: un tasto, un tasto vuoto (niente scritta, per misurare quanto passa
 * il fondo senza che l'etichetta disturbi), una lente con un'icona, l'interruttore, il cursore e
 * una card di vetro col suo tasto vuoto.
 *
 * Ogni controllo riceve **la stessa** registrazione del pannello, `backdrop = stage`, che e'
 * esattamente come le app li scrivono: l'unico stato di vetro che hanno in mano e' quello della
 * pagina. Prima della 2.13.0 cosi' un tasto sul pannello campionava la pagina cruda e veniva piu'
 * nitido del pannello su cui sta; il palco esiste per far vedere (e misurare) che non succede piu'.
 */
@Composable
internal fun GlassOnGlassStage(modifier: Modifier = Modifier) {
  val stage = rememberGlassBackdrop()
  var checked by remember { mutableStateOf(true) }
  var level by remember { mutableFloatStateOf(0.6f) }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(392.dp)
      .clip(ContinuousCornerShape(FluidRadius.Group))
      .testTag("glass-on-glass-stage"),
  ) {
    StageStripes(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(stage),
    )

    Column(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 14.dp)
        .fillMaxWidth()
        .glassSurface(
          state = stage,
          tint = GlassDefaults.modalTint(),
          shape = ContinuousCornerShape(FluidRadius.Sheet),
          role = GlassRole.Modal,
        )
        .padding(horizontal = 16.dp, vertical = 18.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Pannello di vetro",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )
      Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidGlassButton(text = "Attiva", onClick = {}, backdrop = stage)
        BlankGlassPill(stage, Modifier.testTag("stage-blank-pill"))
        FluidGlassIconButton(onClick = {}, backdrop = stage) {
          Icon(Icons.Rounded.Star, contentDescription = "Preferito")
        }
      }
      Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        FluidSwitch(checked = checked, onCheckedChange = { checked = it }, backdrop = stage)
        FluidSlider(
          value = level,
          onValueChange = { level = it },
          modifier = Modifier.weight(1f),
          backdrop = stage,
        )
      }
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(76.dp)
          .glassSurface(
            state = stage,
            tint = GlassDefaults.contentTint(),
            shape = ContinuousCornerShape(FluidRadius.Card),
            role = GlassRole.Content,
          )
          .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        Text(
          text = "Card sul pannello",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BlankGlassPill(stage, Modifier.align(Alignment.CenterEnd))
      }
    }
  }
}

/**
 * Un pannello che il vetro dell'engine non disegna: quello che un'app si fa con un altro motore.
 *
 * Qui e' una copia delle righe sfocata con `Modifier.blur` e velata di bianco — il modo in cui lo
 * fanno le librerie di sfocatura che copiano il fondo. L'engine non lo puo' trovare da solo (non e'
 * un suo nodo di vetro), quindi il pannello lo pubblica con `glassMaterialSource` e il contenuto
 * dichiara di starci sopra con `glassContainer`: e' la strada documentata per Fluidify e simili.
 * Il tasto vuoto riceve il palco, come sempre, e deve venire sfocato quanto il pannello.
 */
@Composable
internal fun ForeignPaneStage(modifier: Modifier = Modifier) {
  val stage = rememberGlassBackdrop()
  val pane = rememberGlassBackdrop()
  val shape = ContinuousCornerShape(FluidRadius.Group)
  Box(
    modifier = modifier
      .fillMaxWidth()
      .height(176.dp)
      .clip(ContinuousCornerShape(FluidRadius.Group)),
  ) {
    StageStripes(
      modifier = Modifier
        .fillMaxSize()
        .glassBackdropSource(stage),
    )
    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .padding(horizontal = 24.dp)
        .fillMaxWidth()
        .height(112.dp),
    ) {
      // Il materiale dell'app: un nodo suo, fratello del contenuto, registrato per chi ci sta sopra.
      Box(
        modifier = Modifier
          .matchParentSize()
          .glassMaterialSource(pane)
          .clip(shape),
      ) {
        StageStripes(modifier = Modifier.matchParentSize().blur(18.dp))
        Box(Modifier.matchParentSize().drawBehind { drawRect(Color.White.copy(alpha = 0.5f)) })
      }
      Row(
        modifier = Modifier
          .matchParentSize()
          .glassContainer(pane)
          .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "Pannello dell'app",
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        BlankGlassPill(stage, Modifier.testTag("foreign-blank-pill"))
      }
    }
  }
}

/**
 * Un tasto di vetro senza niente sopra: il punto dove il fondo si misura senza un'etichetta in mezzo.
 */
@Composable
internal fun BlankGlassPill(backdrop: GlassBackdropState, modifier: Modifier = Modifier) {
  Box(
    modifier = modifier
      .size(width = 88.dp, height = 44.dp)
      .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape),
  )
}

/**
 * Righe dure e testo: cio' che il vetro deve sfocare. Una sfumatura nasconderebbe un vetro rotto
 * (piegare una rampa liscia da' un'altra rampa liscia), le righe no.
 */
@Composable
private fun StageStripes(modifier: Modifier = Modifier) {
  val stripes = remember {
    Brush.linearGradient(
      colorStops = arrayOf(
        0f to Color(0xFFFF3B30),
        0.25f to Color(0xFFFF3B30),
        0.25f to Color(0xFFFFFFFF),
        0.5f to Color(0xFFFFFFFF),
        0.5f to Color(0xFF007AFF),
        0.75f to Color(0xFF007AFF),
        0.75f to Color(0xFF111111),
        1f to Color(0xFF111111),
      ),
      start = Offset.Zero,
      end = Offset(96f, 96f),
      tileMode = TileMode.Repeated,
    )
  }
  Box(modifier = modifier.drawBehind { drawRect(stripes) }) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      repeat(16) { index ->
        Text(
          text = "Testo nitido dietro il vetro, riga ${index + 1} — ABCDEFG 0123456789",
          style = MaterialTheme.typography.bodyMedium,
          fontWeight = FontWeight.Bold,
          color = if (index % 2 == 0) Color.Black else Color.White,
        )
      }
    }
  }
}
