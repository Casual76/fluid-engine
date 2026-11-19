package dev.antigravity.fluidengine.ui.compat

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/*
 * Le firme dei tasti di vetro nella 4.0 (piano «iOS in Android», F6.4): stile, larghezza e taglia d'ambiente si
 * aggiungono **in coda**, con un valore di serie, e le chiamate di prima compilano tutte.
 *
 * Perche' esiste. `FluidGlassButton` prende `style` e `sizing` dopo `maxLines`, cioe' dopo l'ultimo parametro di prima:
 * una chiamata per posizione della 3.x resta quella, e una nuova puo' scrivere anche questi per posizione.
 * `FluidGlassIconButton` (le due forme) prende `style` dopo `grip` e prima del contenuto: la lambda finale resta il
 * contenuto, per posizione fino a `grip` compreso. La taglia di serie passa da `Regular` a quella d'ambiente, che di
 * serie e' `Regular`: senza un fornitore niente cambia. Le app compilano l'engine dai sorgenti, quindi la prova e' la
 * compilazione; le funzioni @Composable non si eseguono.
 */

// --- Le forme della 3.x e della 4.0 prima di F6.4, che restano ----------------------------------------------------

@Composable
internal fun GlassButtonCallsBeforeF64(onClick: () -> Unit, backdrop: GlassBackdropState) {
  FluidGlassButton("Fatto", onClick)
  FluidGlassButton(text = "Fatto", onClick = onClick, size = FluidGlassSize.Compact, maxLines = 1)
  // Per posizione, fino a `maxLines`: la forma piu' lunga che un'app potesse scrivere.
  FluidGlassButton(
    "Fatto", onClick, Modifier, backdrop, true, false, FluidCapsuleShape, FluidGlassSize.Large,
    FluidHapticEvent.Tap, FluidPressGrip.HoldsFinger, { FluidIcon(FluidIcons.Check, null) }, 1,
  )
  FluidGlassIconButton(onClick = onClick) { FluidIcon(FluidIcons.Share, "Condividi") }
  // Per posizione fino a `grip`, con il contenuto come lambda finale.
  FluidGlassIconButton(
    onClick, Modifier, backdrop, true, false, FluidCapsuleShape, null, FluidGlassSize.Compact,
    FluidHapticEvent.Tap, FluidHapticEvent.GestureStart, FluidPressGrip.YieldsToScroll,
  ) { FluidIcon(FluidIcons.Share, "Condividi") }
  FluidGlassIconButton(onClick, 76.dp, Modifier, backdrop, true, false, FluidCapsuleShape, null) {
    FluidIcon(FluidIcons.Filled.PlayArrow, "Riproduci")
  }
}

// --- Le forme nuove di F6.4 --------------------------------------------------------------------------------------

@Composable
internal fun GlassButtonCallsInF64(onClick: () -> Unit, backdrop: GlassBackdropState) {
  // Gli stili di SwiftUI, per nome.
  FluidGlassButton("Fatto", onClick, style = FluidGlassButtonStyle.Prominent)
  FluidGlassButton("Annulla", onClick, style = FluidGlassButtonStyle.Glass)
  FluidGlassButton("Sopra la foto", onClick, style = FluidGlassButtonStyle.Clear)
  FluidGlassButton("Elimina", onClick, style = FluidGlassButtonStyle.Prominent(Color(0xFFD32F2F)))
  FluidGlassButton("Tinto", onClick, style = FluidGlassButtonStyle.Glass(FluidGlass.Regular.tint(Color(0xFF0A84FF))))
  FluidGlassButton("Senza vetro", onClick, style = FluidGlassButtonStyle.Glass(FluidGlass.Identity))
  FluidGlassButton("Largo", onClick, sizing = FluidButtonSizing.Flexible)
  // Per posizione, in coda: `style` e `sizing` dopo `maxLines`.
  FluidGlassButton(
    "Fatto", onClick, Modifier, backdrop, true, false, FluidCapsuleShape, FluidGlassSize.Regular,
    FluidHapticEvent.Tap, FluidPressGrip.HoldsFinger, null, 1, FluidGlassButtonStyle.Prominent, FluidButtonSizing.Flexible,
  )
  FluidGlassIconButton(onClick = onClick, style = FluidGlassButtonStyle.Prominent) { FluidIcon(FluidIcons.Check, "Fatto") }
  FluidGlassIconButton(onClick = onClick, size = 62.dp, style = FluidGlassButtonStyle.Clear) {
    FluidIcon(FluidIcons.Filled.PlayArrow, "Riproduci")
  }
  // Per posizione, `style` dopo `grip`, e il contenuto resta la lambda finale.
  FluidGlassIconButton(
    onClick, Modifier, backdrop, true, false, FluidCapsuleShape, null, FluidGlassSize.Regular,
    FluidHapticEvent.Tap, null, FluidPressGrip.HoldsFinger, FluidGlassButtonStyle.Clear,
  ) { FluidIcon(FluidIcons.Share, "Condividi") }

  // L'ambiente: taglia, stile e larghezza per un gruppo di tasti, come `controlSize` e `buttonStyle` di SwiftUI.
  CompositionLocalProvider(
    LocalFluidGlassSize provides FluidGlassSize.Compact,
    LocalFluidGlassButtonStyle provides FluidGlassButtonStyle.Clear,
    LocalFluidButtonSizing provides FluidButtonSizing.Flexible,
  ) {
    FluidGlassIconButton(onClick = onClick) { FluidIcon(FluidIcons.Share, "Condividi") }
    FluidGlassButton("Invia", onClick)
    // Scritta sul tasto, la taglia vince sull'ambiente.
    FluidGlassButton("Grande", onClick, size = FluidGlassSize.Large)
    Text("${LocalFluidGlassSize.current} ${LocalFluidGlassButtonStyle.current} ${LocalFluidButtonSizing.current}")
  }
}

class ButtonStyleCompatSignaturesTest {

  @Test
  fun `gli stili sono valori, uguali se uguali`() {
    assertSame(FluidGlassButtonStyle.Glass, FluidGlassButtonStyle.Glass(FluidGlass.Regular))
    assertEquals("FluidGlassButtonStyle.Prominent", FluidGlassButtonStyle.Prominent.toString())
    assertEquals("FluidGlassButtonStyle.Clear", FluidGlassButtonStyle.Clear.toString())
    assertEquals(listOf(FluidButtonSizing.Fitted, FluidButtonSizing.Flexible), FluidButtonSizing.entries)
  }
}
