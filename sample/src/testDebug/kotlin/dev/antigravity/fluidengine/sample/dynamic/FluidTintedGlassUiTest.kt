@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.dynamic

import androidx.compose.runtime.remember
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.FluidTintedGlassKey
import dev.antigravity.fluidengine.ui.fluid.FluidTintedGlassMark
import dev.antigravity.fluidengine.ui.fluid.fluidTintedGlassColor
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.glassPaintedRecipe
import dev.antigravity.fluidengine.ui.theme.FluidDefaultBrand
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Il vetro tinto di F6B.16 in un albero Compose vero, la scena `vetro-tinto` della galleria, in chiaro e in scuro: ogni
 * elemento tinto (la pillola scelta, il tasto in evidenza con l'etichetta e tondo, il pulsante pieno) **si registra come
 * vetro tinto** del materiale nuovo, vero dove sta sopra una cattura e dipinto altrove, e il vetro che disegna ha il filo
 * di luce. Il contrasto dello scritto (almeno 4,5:1 su ogni accento, in chiaro e in scuro) è aritmetica: lo prova
 * `FluidTintedGlassTest` nel modulo dell'engine.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h1400dp-xhdpi")
class FluidTintedGlassUiTest {
  @get:Rule val compose = createComposeRule()

  private inline fun probed(block: () -> Unit) {
    val enabled = FluidGlassDiagnostics.enabled
    val warm = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    try {
      block()
    } finally {
      FluidGlassDiagnostics.resetForTesting()
      FluidGlassDiagnostics.enabled = enabled
      FluidGlassDiagnostics.warmUpEnabled = warm
    }
  }

  private fun scene(dark: Boolean) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }) {
        TintedGlassScene(onBack = {})
      }
    }
    compose.waitForIdle()
  }

  /** Il segno letto dal suo testo (la classe è interna all'engine: da qui se ne legge solo `toString`). */
  private class Mark(val painted: Boolean, val liquid: Boolean)

  private fun Any.asMark(): Mark {
    val text = toString()
    fun field(name: String) = Regex("$name=([^,)]+)").find(text)?.groupValues?.get(1) ?: error("$name in $text")
    return Mark(field("painted").toBoolean(), field("liquid").toBoolean())
  }

  /** Il segno del vetro tinto sul nodo con [tag] o su uno dei suoi discendenti (il vetro di un tasto tondo sta dentro). */
  private fun mark(tag: String): Mark? {
    val own: Any? = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes()
      .firstNotNullOfOrNull { node: SemanticsNode -> node.config.getOrNull(FluidTintedGlassKey) }
    if (own != null) return own.asMark()
    val inner: Any? = compose.onAllNodes(SemanticsMatcher.keyIsDefined(FluidTintedGlassKey) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
      .fetchSemanticsNodes().firstNotNullOfOrNull { node: SemanticsNode -> node.config.getOrNull(FluidTintedGlassKey) }
    return inner?.asMark()
  }

  private fun checkScene(dark: Boolean) = probed {
    scene(dark)
    val side = if (dark) "scuro" else "chiaro"
    for (ground in TintedGlassGround.entries) {
      val tags = listOf(
        "${TintedGlassTags.Pill}:${ground.key}",
        "${TintedGlassTags.Prominent}:${ground.key}",
        "${TintedGlassTags.ProminentIcon}:${ground.key}",
        "${TintedGlassTags.Filled}:${ground.key}",
      )
      for (tag in tags) {
        val mark = mark(tag)
        assertNotNull("$side, $tag: si registra come vetro tinto", mark)
        assertTrue("$side, $tag: è il materiale nuovo", mark!!.liquid)
      }
      if (ground.photo != null) {
        // Sopra una foto c'è una cattura: la pillola scelta e il tasto in evidenza sono vetro vero, non dipinto.
        assertTrue("$side, ${ground.key}: la pillola è vetro vero", !mark("${TintedGlassTags.Pill}:${ground.key}")!!.painted)
        assertTrue("$side, ${ground.key}: il tasto in evidenza è vetro vero", !mark("${TintedGlassTags.Prominent}:${ground.key}")!!.painted)
      }
      // Il pulsante pieno è vetro dipinto: non ha una cattura sua.
      assertTrue("$side, ${ground.key}: il pulsante pieno è vetro dipinto", mark("${TintedGlassTags.Filled}:${ground.key}")!!.painted)
    }
    val liquid = FluidGlassDiagnostics.liveSurfaces[FluidGlassModel.Liquid] ?: 0
    assertTrue("$side: le superfici del materiale nuovo sono in scena ($liquid)", liquid > 0)
  }

  @Test fun `in chiaro ogni elemento tinto e' vetro tinto del materiale nuovo`() = checkScene(dark = false)

  @Test fun `in scuro ogni elemento tinto e' vetro tinto del materiale nuovo`() = checkScene(dark = true)

  @Test fun `il vetro tinto dipinto ha il filo di luce, il riflesso e l'ombra`() {
    for (dark in listOf(false, true)) {
      val tint = fluidTintedGlassColor(FluidDefaultBrand.resolve(dark))
      val recipe = glassPaintedRecipe(96f, 40f, dark, tint = tint)
      val plain = glassPaintedRecipe(96f, 40f, dark)
      assertTrue("filo", recipe.drawsRim)
      assertTrue("riflesso", recipe.drawsReflex)
      assertTrue("ombra", recipe.drawsShadow)
      assertTrue("il filo prende la tinta", recipe.rimDark != plain.rimDark)
      assertTrue("la tinta è quella di Apple", recipe.tintMode == 1f && recipe.tintColor != Color.Transparent)
    }
  }
}
