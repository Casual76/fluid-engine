package dev.antigravity.fluidengine.sample.playground

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSegmentedControl
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsContentRole
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsTier
import dev.antigravity.fluidengine.ui.fluidphysics.fluidPhysicsContent
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidPhysicsState
import kotlinx.coroutines.launch

/**
 * Il lato pratico del motore: due tasti che si fondono in un menù, il menù che si allarga in un
 * pop-up, e la strada inversa. La stessa meccanica che un domani vestirà i componenti veri
 * dell'engine, dimostrata con la sola API pubblica di Fluid-physics.
 *
 * Il contenuto segue il contratto: ogni fase ha il suo strato, misurato alla propria taglia
 * d'arrivo, che entra ed esce con [fluidPhysicsContent] — la sagoma si trasforma, le scritte mai.
 */
internal enum class DemoPhase(val label: String) {
  Buttons("Tasti"),
  Menu("Menù"),
  Popup("Pop-up"),
}

@Composable
internal fun PracticalDemo(tier: FluidPhysicsTier?) {
  val density = LocalDensity.current
  val groupRadiusPx = with(density) { FluidRadius.Group.toPx() }
  val sheetRadiusPx = with(density) { FluidRadius.Sheet.toPx() }

  val state = rememberFluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f))
  var stage by remember { mutableStateOf(IntSize.Zero) }
  var phase by remember { mutableStateOf(DemoPhase.Buttons) }
  val scope = rememberCoroutineScope()

  fun formFor(target: DemoPhase): FluidForm =
    demoFormFor(target, stage, groupRadiusPx, sheetRadiusPx)

  // Il palco si misura dopo la prima composizione (e di nuovo a ogni rotazione): la geometria
  // arriva senza viaggiare, perché non c'è nessun viaggio da mostrare — solo una taglia nuova.
  LaunchedEffect(stage) {
    if (stage != IntSize.Zero) state.snapTo(formFor(phase))
  }

  Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    PlaygroundStage(
      state = state,
      tier = tier,
      drawing = false,
      aspectRatio = 1.25f,
      onSized = { stage = it },
    ) {
      if (stage != IntSize.Zero) {
        DemoContents(state = state, phase = phase, stage = stage)
      }
    }

    FluidSegmentedControl(
      options = DemoPhase.entries.toList(),
      selected = phase,
      onSelect = { target ->
        if (target != phase && stage != IntSize.Zero) {
          phase = target
          scope.launch { state.morphTo(formFor(target)) }
        }
      },
      label = { it.label },
    )

    Text(
      text = "Due pezzi e un pannello sono la stessa superficie: avvicinali e si versano l'uno " +
        "nell'altro. Il contenuto non si stira mai — cambia strato, come nei modali veri.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Le tre geometrie del racconto, in pixel del palco. */
private fun demoFormFor(
  phase: DemoPhase,
  stage: IntSize,
  groupRadiusPx: Float,
  sheetRadiusPx: Float,
): FluidForm {
  val w = stage.width.toFloat()
  val h = stage.height.toFloat()
  return when (phase) {
    DemoPhase.Buttons -> {
      val buttonWidth = w * 0.34f
      val buttonHeight = h * 0.17f
      val gap = w * 0.07f
      val top = h * 0.5f - buttonHeight / 2f
      val leftFrame = Rect(w / 2f - gap / 2f - buttonWidth, top, w / 2f - gap / 2f, top + buttonHeight)
      val rightFrame = Rect(w / 2f + gap / 2f, top, w / 2f + gap / 2f + buttonWidth, top + buttonHeight)
      FluidForm.Group(
        pieces = listOf(
          FluidForm.Slab(leftFrame, FluidCornerRadii.all(buttonHeight / 2f)),
          FluidForm.Slab(rightFrame, FluidCornerRadii.all(buttonHeight / 2f)),
        ),
        blendRadius = minOf(w, h) * 0.11f,
      )
    }

    DemoPhase.Menu -> {
      val menuWidth = w * 0.58f
      val menuHeight = h * 0.62f
      FluidForm.Slab(
        frame = Rect(
          w / 2f - menuWidth / 2f,
          h * 0.48f - menuHeight / 2f,
          w / 2f + menuWidth / 2f,
          h * 0.48f + menuHeight / 2f,
        ),
        cornerRadii = FluidCornerRadii.all(groupRadiusPx),
      )
    }

    DemoPhase.Popup -> {
      val popupWidth = w * 0.84f
      val popupHeight = h * 0.72f
      FluidForm.Slab(
        frame = Rect(
          w / 2f - popupWidth / 2f,
          h * 0.5f - popupHeight / 2f,
          w / 2f + popupWidth / 2f,
          h * 0.5f + popupHeight / 2f,
        ),
        cornerRadii = FluidCornerRadii.all(sheetRadiusPx),
      )
    }
  }
}

/** I tre strati di contenuto, ognuno alla taglia della propria fase. */
@Composable
private fun DemoContents(state: FluidPhysicsState, phase: DemoPhase, stage: IntSize) {
  fun roleFor(own: DemoPhase): FluidPhysicsContentRole =
    if (phase == own) FluidPhysicsContentRole.Incoming else FluidPhysicsContentRole.Outgoing

  val w = stage.width.toFloat()
  val h = stage.height.toFloat()

  // --- Tasti ---
  run {
    val buttonWidth = w * 0.34f
    val buttonHeight = h * 0.17f
    val gap = w * 0.07f
    val top = h * 0.5f - buttonHeight / 2f
    FrameBox(
      frame = Rect(w / 2f - gap / 2f - buttonWidth, top, w / 2f - gap / 2f, top + buttonHeight),
      modifier = Modifier.fluidPhysicsContent(state, roleFor(DemoPhase.Buttons)),
    ) {
      DemoLabel(icon = Icons.Rounded.Share, text = "Condividi")
    }
    FrameBox(
      frame = Rect(w / 2f + gap / 2f, top, w / 2f + gap / 2f + buttonWidth, top + buttonHeight),
      modifier = Modifier.fluidPhysicsContent(state, roleFor(DemoPhase.Buttons)),
    ) {
      DemoLabel(icon = Icons.Rounded.Delete, text = "Elimina")
    }
  }

  // --- Menù ---
  run {
    val menuWidth = w * 0.58f
    val menuHeight = h * 0.62f
    FrameBox(
      frame = Rect(
        w / 2f - menuWidth / 2f,
        h * 0.48f - menuHeight / 2f,
        w / 2f + menuWidth / 2f,
        h * 0.48f + menuHeight / 2f,
      ),
      modifier = Modifier.fluidPhysicsContent(state, roleFor(DemoPhase.Menu)),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
      ) {
        DemoMenuRow(Icons.Rounded.Share, "Condividi")
        DemoMenuRow(Icons.Rounded.Star, "Aggiungi ai preferiti")
        DemoMenuRow(Icons.Rounded.Delete, "Elimina", destructive = true)
      }
    }
  }

  // --- Pop-up ---
  run {
    val popupWidth = w * 0.84f
    val popupHeight = h * 0.72f
    FrameBox(
      frame = Rect(
        w / 2f - popupWidth / 2f,
        h * 0.5f - popupHeight / 2f,
        w / 2f + popupWidth / 2f,
        h * 0.5f + popupHeight / 2f,
      ),
      modifier = Modifier.fluidPhysicsContent(state, roleFor(DemoPhase.Popup)),
    ) {
      Column(
        modifier = Modifier.fillMaxSize().padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "Un pop-up di vetro",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = Color.White,
        )
        Text(
          text = "Arrivato qui dai due tasti, passando dal menù, senza mai smettere di essere " +
            "la stessa superficie. La pagina dietro resta leggibile attraverso il materiale.",
          style = MaterialTheme.typography.bodySmall,
          color = Color.White.copy(alpha = 0.92f),
        )
      }
    }
  }
}

/** Posa un contenuto esattamente sul frame in pixel della sua fase. */
@Composable
private fun FrameBox(
  frame: Rect,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val density = LocalDensity.current
  Box(
    modifier = Modifier
      .offset { IntOffset(frame.left.toInt(), frame.top.toInt()) }
      .size(
        width = with(density) { frame.width.toDp() },
        height = with(density) { frame.height.toDp() },
      )
      .then(modifier),
    contentAlignment = Alignment.Center,
  ) {
    content()
  }
}

@Composable
private fun DemoLabel(icon: ImageVector, text: String) {
  Row(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.SemiBold,
      color = Color.White,
    )
  }
}

@Composable
private fun DemoMenuRow(icon: ImageVector, text: String, destructive: Boolean = false) {
  val tint = if (destructive) Color(0xFFFF6B6B) else Color.White
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.bodyMedium,
      color = tint,
    )
  }
}
