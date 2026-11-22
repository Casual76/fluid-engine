package dev.antigravity.fluidengine.sample.compat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.richtext.FluidRichTextState
import dev.antigravity.fluidengine.richtext.FluidRichTextToolbar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsulePlacement
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import org.junit.Assert.assertEquals
import org.junit.Test

/** Il sample compila con i due moduli che un'app importerebbe per usare la barra del testo ricco. */
class GlassCompatSignaturesTest {
  @Test fun richTextToolbarKeepsItsSixParameterTypes() {
    val owner = Class.forName("dev.antigravity.fluidengine.richtext.FluidRichTextToolbarKt")
    val publicSignature = owner.declaredMethods.single { it.name == "FluidRichTextToolbar" }
    assertEquals(listOf(
      FluidRichTextState::class.java, Modifier::class.java, GlassBackdropState::class.java,
      FluidFloatingCapsulePlacement::class.java, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
    ), publicSignature.parameterTypes.take(6))
  }
}

@Composable
private fun richTextToolbarCallsAsInThe3x(state: FluidRichTextState, modifier: Modifier, backdrop: GlassBackdropState) {
  FluidRichTextToolbar(state)
  FluidRichTextToolbar(state, modifier, backdrop, FluidFloatingCapsulePlacement.Free, false, false)
  FluidRichTextToolbar(state = state, modifier = modifier, backdrop = backdrop,
    placement = FluidFloatingCapsulePlacement.AboveTabBar, showHistory = false, avoidKeyboard = false)
}
