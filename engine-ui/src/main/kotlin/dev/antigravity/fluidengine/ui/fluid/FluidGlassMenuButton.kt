package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A button that unfolds into its own menu, and folds back once something is chosen.
 *
 * Tapped it performs [onClick], the way any button does. **Held**, it does not open a pop-up beside
 * itself — it *becomes* one: the menu starts at exactly this button's rectangle, made of the same
 * glass, and grows out of it on the two springs the navigation pill uses. The button steps aside
 * while that happens, so what the eye follows is one object changing size rather than a second
 * object arriving on top of the first. Choosing an action reverses it back into the button.
 *
 * That difference is the whole component. A menu that appears *next to* its button is a menu; a
 * button that turns into its menu is the thing Apple's controls do, and the reason it reads as
 * physical is that at no point are there two of it.
 *
 * The unfolded pane is drawn by [FluidGlassModalHost] at the root of the app, so it clears the tab
 * bar and anything else the page is sitting under. With no host in scope the long press does
 * nothing and the button is an ordinary button — which is the right degradation, not a crash.
 *
 * **Deprecato** in favore di [dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton], e a
 * dividerli è la forma del menù, non il nome. Qui il pannello dell'host *compare sopra* il tasto:
 * sono due superfici e una staffetta fra loro, per quanto vicina. Il tasto nuovo è un'ancora sola, e
 * la superficie che viaggia dal tasto al menù è una sola, scolpita da Fluid-physics: parte dalla
 * capsula, arriva al pannello, e al congedo torna capsula.
 *
 * Non c'è una sostituzione a una riga, perché il nuovo ha bisogno di una cosa che questo non ha: un
 * host suo alla radice dell'app. Per migrare:
 *
 *  1. alla radice, accanto al [FluidGlassModalHost], `val menu = rememberFluidMorphMenuState()` e
 *     `FluidMorphMenuHost(state = menu, backdrop = backdrop)`, disegnato sopra la tab bar;
 *  2. dove c'era `FluidGlassMenuButton(actions, onClick, text = …, icon = …)`, ora c'è
 *     `FluidMorphMenuButton(state = menu, actions = actions, onClick = onClick, text = …,
 *     icon = …, backdrop = backdrop)`. Lo stato arriva da un composition local o da un parametro,
 *     come l'host dei modali.
 *
 * Due parametri non hanno corrispondente: [shape], perché la superficie che viaggia parte sempre da
 * una capsula, e [contentDescription], perché l'etichetta del tasto è già quella che si legge.
 *
 * Le azioni di barra che una schermata dichiara con `FluidBarAction(actions = …)` **non passano di
 * qui** e non cambiano: continuano a funzionare così come sono.
 */
@Deprecated(
  message = "Il menù compare sopra il tasto: due superfici. FluidMorphMenuButton (con " +
    "FluidMorphMenuHost alla radice) fa viaggiare il tasto stesso fino a diventare il menù. " +
    "Non ha una sostituzione a una riga: serve lo stato e l'host, vedi il KDoc.",
  level = DeprecationLevel.WARNING,
)
@Composable
fun FluidGlassMenuButton(
  actions: () -> List<FluidContextAction>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  icon: ImageVector? = null,
  contentDescription: String? = null,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  shape: Shape = FluidCapsuleShape,
) {
  val host = LocalFluidGlassModalHostState.current
  val entry = remember { FluidGlassModalEntry() }
  val haptics = LocalFluidHaptics.current
  val anchor = remember { FluidMenuAnchor() }
  // La pressione del vetro, condivisa: al click lungo serve il tasto COME IL DITO LO STA GONFIANDO,
  // da cui parte la sagoma del menu (3.0).
  val press = rememberFluidPressState(FluidPressFeedback.Expand)
  val density = LocalDensity.current.density
  val tint = GlassDefaults.controlTint()
  val faceColor = MaterialTheme.colorScheme.onSurface

  SideEffect {
    entry.presentation = FluidGlassModalPresentation.Expand
    entry.paneTitle = text ?: contentDescription
    entry.onDismissRequest = { entry.visible = false }
    entry.menuAnchor = anchor
    entry.menuHandOff = anchor.handOff
    anchor.face = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(imageVector = it, contentDescription = null, modifier = Modifier.size(20.dp), tint = faceColor) }
        text?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = faceColor,
            maxLines = 1,
          )
        }
      }
    }
    anchor.tintFrom = tint
    anchor.opticsFrom = GlassDefaults.buttonOptics()
  }

  DisposableEffect(host, entry) {
    host?.register(entry)
    onDispose {
      entry.visible = false
      anchor.hidden = false
      host?.unregister(entry)
    }
  }

  // Il click lungo, nel gesto e sincrono: niente LaunchedEffect che apre un fotogramma dopo.
  val openHeld: () -> Unit = open@{
    if (host == null || entry.visible) return@open
    val list = actions()
    if (list.isEmpty()) return@open
    anchor.pressedBounds = fluidPressedBounds(anchor.restBounds, press, density)
    entry.actions = list
    entry.openedAt = host.nextSequence()
    entry.visible = true
    entry.lifted = true
    anchor.hidden = true
    entry.selection.cancel()
    anchor.handOff.attach(entry.selection)
    haptics.play(FluidHapticEvent.Open)
  }

  Box(
    modifier = modifier
      // Il relè in testa (3.0): il dito che tiene premuto passa al menu, e il menu si sceglie
      // trascinando e rilasciando su una voce.
      .fluidMenuPointerRelay(anchor.handOff)
      .onGloballyPositioned { coordinates ->
        val rect = coordinates.unclippedBoundsInRoot()
        anchor.restBounds = rect
        entry.previewSize = rect
        entry.origin = { rect }
      }
      // The button gives up its pixels while the menu is out, and keeps its *space*. Removing it from
      // the layout instead would reflow the row it sits in at the exact moment the menu is growing out
      // of where it used to be. Dalla 3.0 e' un cancello nel disegno, non una dissolvenza con una molla
      // sua: la finestra compare sulla sua sagoma nello stesso fotogramma.
      .drawWithContent { if (!anchor.hidden) drawContent() }
      .height(FluidGlassMenuButtonHeight)
      .defaultMinSize(minWidth = FluidGlassMenuButtonHeight)
      // The full control treatment, not a plain pane: it leans toward the finger, swells while it
      // is held and lights up where it was touched.
      .glassControlSurface(
        backdrop = backdrop,
        shape = shape,
        interactive = enabled,
        pressState = press,
      )
      // Un tasto come gli altri (3.0): la pressione e' condivisa col vetro, il dito e' suo fino al
      // sollevamento, il click anche dopo un trascinamento. Il click lungo apre, e non ha un colpo suo:
      // l'`Open` lo suona `openHeld`, solo se il menu si e' aperto.
      .fluidPressable(
        pressState = press,
        onClick = onClick,
        onLongClick = openHeld,
        enabled = enabled,
        role = Role.Button,
        longClickHaptic = null,
      )
      .padding(horizontal = if (text != null) 18.dp else 0.dp),
    contentAlignment = Alignment.Center,
  ) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        icon?.let {
          Icon(
            imageVector = it,
            contentDescription = if (text == null) contentDescription else null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
          )
        }
        text?.let {
          Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

/** 48 dp: the smallest a control may be and still be reliably hit. */
private val FluidGlassMenuButtonHeight = 48.dp
