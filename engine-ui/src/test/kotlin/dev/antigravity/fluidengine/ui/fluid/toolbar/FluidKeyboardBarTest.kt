package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchNavigation
import dev.antigravity.fluidengine.ui.fluid.FluidTokenSuggestion
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.FormatBold
import dev.antigravity.fluidengine.ui.fluid.icons.FormatItalic
import dev.antigravity.fluidengine.ui.fluid.icons.FormatUnderlined
import dev.antigravity.fluidengine.ui.glass.field.trace.glassTraceTimeNanos
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidKeyboardBarTest {
  @Test fun takesEveryAnimatedHeightWithoutWaitingForTheSpring() {
    val heights = listOf(0, 1, 12, 37, 82, 126, 217, 256, 288, 300)
    for ((source, target) in listOf(0 to 300, 300 to 0)) {
      var followingPlatform = false
      for (height in if (source < target) heights else heights.asReversed()) {
        val sample = KeyboardImeSample(height, source, target)
        assertEquals(height, sample.layoutBottom(followingPlatform, reduced = false, springBottom = 0))
        followingPlatform = followingPlatform || sample.hasIntermediateHeight
      }
      val settled = KeyboardImeSample(target, target, target)
      assertEquals(target, settled.layoutBottom(followingPlatform = true, reduced = false, springBottom = source))
    }
  }

  @Test fun lateImeUsesTheStandardSpringUntilARealAnimationTakesOver() {
    val late = KeyboardImeSample(260, 260, 260)
    assertFalse(late.hasPlatformMotion)
    val standard = FluidMotion.standard<Float>()
    val spring = FloatSpringSpec(standard.dampingRatio, standard.stiffness, 1f)
    val heights = (0..90).map { frame ->
      val interpolated = spring.getValueFromNanos(glassTraceTimeNanos(frame, 120), 0f, 260f, 0f).roundToInt()
      late.layoutBottom(followingPlatform = false, reduced = false, springBottom = interpolated)
    }
    assertEquals(0, heights.first())
    assertTrue(heights[1] in 1..259)
    assertEquals(260, heights.last())
    assertEquals(260, late.layoutBottom(followingPlatform = false, reduced = true, springBottom = 0))
    val jumpingAnimation = KeyboardImeSample(260, 0, 260)
    assertTrue(jumpingAnimation.hasPlatformMotion)
    assertFalse(jumpingAnimation.hasIntermediateHeight)
    assertEquals(heights[6],
      jumpingAnimation.layoutBottom(followingPlatform = false, reduced = false, springBottom = heights[6]))
    val animated = KeyboardImeSample(184, 260, 0)
    assertTrue(animated.hasPlatformMotion)
    assertEquals(184, animated.layoutBottom(followingPlatform = false, reduced = false, springBottom = heights[6]))
  }

  @Test fun physicalAndFloatingKeyboardsStayAtTheBottom() {
    val detached = KeyboardImeSample(0, 0, 0)
    assertEquals(0, detached.layoutBottom(followingPlatform = false, reduced = false, springBottom = 0))
    assertTrue(fluidKeyboardBarVisible(false, true, true, false, true))
    assertTrue(fluidKeyboardBarVisible(false, true, true, true, false))
    assertFalse(fluidKeyboardBarVisible(false, true, false, false, true))
  }

  @Test fun backHidesTheAutomaticBarEvenWhileTextKeepsFocus() {
    assertTrue(fluidKeyboardBarVisible(false, true, true, true, false))
    assertFalse(fluidKeyboardBarVisible(false, true, true, false, false))
    assertFalse(fluidKeyboardBarVisible(false, false, true, true, false))
    assertFalse(fluidKeyboardBarVisible(false, true, false, true, false))
    assertTrue(fluidKeyboardBarVisible(true, true, false, false, false))
  }

  @Test fun aKeyboardDeclarationDoesNotConsumeTopBarIdsOrBack() {
    val toolbar = buildFluidScreenToolbar({}) {
      button("same", FluidIcons.Check, "In alto", {})
      keyboard("editor") { button("same", FluidIcons.FormatBold, "Grassetto", {}) }
    }
    assertEquals("In alto", toolbar.buttons.getValue("same").label)
    assertEquals("Grassetto", toolbar.keyboard!!.bar.buttons.getValue("same").label)
    assertTrue(toolbar.back != null)
    assertFalse(toolbar.hasBottomBar)
  }

  @Test fun newContextChangesFacesOnTheSameGlassAndResizesIt() {
    val first = resolveKeyboard("suggestions") {
      button("same", FluidIcons.Check, "Accetta", {})
    }
    val second = resolveKeyboard("format") {
      button("same", FluidIcons.FormatBold, "Grassetto", {})
      button("italic", FluidIcons.FormatItalic, "Corsivo", {})
    }
    val memory = FluidToolbarMemory()
    memory.update(first, FluidToolbarMotion.Full, ridesEdge = true)
    val retained = memory.glassIds.values.single()
    memory.update(second, FluidToolbarMotion.Full, ridesEdge = true)
    assertEquals(retained, memory.glassIds.values.single())
    assertTrue(second.groups.single().widthDp > first.groups.single().widthDp)
    assertNotEquals(first.element("same")?.item?.glassContent, second.element("same")?.item?.glassContent)
    assertEquals(FluidToolbarContentChange.BlurReplace,
      diffFluidToolbar(first, second).elements.single { it.from?.item?.id == "same" }.content)
  }

  @Test fun newCommandsWithNoSharedIdsStillInheritTheGlassForTheirSide() {
    val first = resolveKeyboard("before") {
      button("old", FluidIcons.Check, "Accetta", {})
    }
    val second = resolveKeyboard("after") {
      button("bold", FluidIcons.FormatBold, "Grassetto", {})
      button("underline", FluidIcons.FormatUnderlined, "Sottolinea", {})
    }
    val memory = FluidToolbarMemory()
    memory.update(first, FluidToolbarMotion.Full)
    val retained = memory.glassIds.values.single()
    memory.update(second, FluidToolbarMotion.Full)
    assertEquals(retained, memory.glassIds.values.single())
    assertEquals(setOf("old"), memory.ghosts.map { it.element.item.id }.toSet())
    assertEquals(setOf("bold", "underline"), memory.arrivals.keys)
  }

  @Test fun searchRowFitsAboveTheKeyboardCommandsAtEveryHeight() {
    val searchHeight = fluidToolbarMetrics().buttonWidthDp
    for (ime in listOf(0, 8, 32, 143, 279)) {
      val barTop = 600 - ime - FluidKeyboardBarHeight.value
      val searchTop = toolbarSearchOffset(600 - ime - FluidKeyboardSearchClearance.value.toInt(), searchHeight.toInt(), 1f)
      val searchBottom = searchTop + searchHeight
      assertTrue(searchBottom < barTop)
      assertTrue(searchBottom - searchHeight > 0)
    }
  }

  @Test fun suggestionsKeepTheirIdAndOverflowBeforeHighPriorityActions() {
    val picks = mutableListOf<String>()
    val definition = buildFluidToolbar(null) {
      suggestions(List(5) { FluidTokenSuggestion("id-$it", "Destinatario $it") }) { picks += it.id }
      findInText("lago", FluidSearchNavigation(2, 12, {}, {}))
    }
    val result = resolveFluidToolbar(fluidToolbarModel(definition, { 104f }, keyboardContext = "recipients"),
      320f, metrics = fluidBottomBarMetrics(), bar = FluidToolbarBar.Keyboard)
    assertTrue(result.overflow.any { it.label?.startsWith("Destinatario ") == true })
    assertTrue(result.elements.any { it.item.kind?.type == FluidToolbarKinds.KeyboardFindCounter })
    definition.buttons.values.first { it.text == "Destinatario 0" }.onClick?.invoke()
    assertEquals(listOf("id-0"), picks)
  }
}

internal fun resolveKeyboard(context: Any, content: FluidToolbarScope.() -> Unit): FluidToolbarResolved {
  val declaration = buildFluidToolbar(null) { keyboard(context, content = content) }.keyboard!!
  return resolveFluidToolbar(fluidToolbarModel(declaration.bar, { 92.dp.value }, keyboardContext = declaration.context),
    400f, metrics = fluidBottomBarMetrics(), bar = FluidToolbarBar.Keyboard)
}
