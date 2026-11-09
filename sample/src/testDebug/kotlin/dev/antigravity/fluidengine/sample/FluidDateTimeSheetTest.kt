package dev.antigravity.fluidengine.sample

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class PickerClock(var value: Instant) : Clock() {
  override fun getZone(): ZoneId = ZoneOffset.UTC
  override fun withZone(zone: ZoneId): Clock = this
  override fun instant(): Instant = value
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w400dp-h900dp-xhdpi", sdk = [35])
class FluidDateTimeSheetTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private val restoration = StateRestorationTester(compose)
  private val clock = PickerClock(Instant.parse("2026-11-09T10:00:00Z"))
  private val next = clock.instant().plusSeconds(3600)
  private var results = mutableListOf<FluidDateTimeSelection>()
  private var cancelled = 0
  private fun show(custom: Boolean = false, large: Boolean = false, english: Boolean = false,
    initial: Instant = next, zone: ZoneId = ZoneId.of("Europe/Rome"), min: Instant? = null, max: Instant? = null) {
    restoration.setContent {
      FluidTheme(EngineSettings(themeMode = if (large) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val host = rememberFluidGlassModalHostState()
        val density = LocalDensity.current
        var open by remember { mutableStateOf(true) }
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host,
          LocalDensity provides Density(density.density, if (large) 2f else 1f),
          LocalFluidLabels provides if (english) FluidLabels.english() else FluidLabels(),
          LocalFluidMotionPolicy provides FluidMotionPolicy(0f)) {
          Box(Modifier.fillMaxSize()) {
            FluidDateTimeSheet(open, { cancelled++; open = false }, { results += it },
              quickOptions = if (custom) emptyList() else listOf(FluidQuickTimeOption("Tomorrow", next)),
              clock = clock, locale = if (english) Locale.US else Locale.ITALY, zone = zone,
              initialInstant = initial, use24Hour = !english, minimumInstant = min, maximumInstant = max)
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    compose.waitForIdle()
  }
  @Test fun quickSelectionConfirmsExactlyOnceWithZone() {
    show()
    compose.onNodeWithText("Tomorrow").performClick()
    compose.onNodeWithText("Conferma").performClick()
    compose.onNodeWithText("Conferma").assertIsNotEnabled().performClick()
    assertEquals(listOf(FluidDateTimeSelection(next, ZoneId.of("Europe/Rome"))), results)
    assertEquals(0, cancelled)
  }
  @Test fun cancelDoesNotConfirm() {
    show()
    compose.onNodeWithText("Annulla").performClick()
    assertEquals(1, cancelled)
    assertTrue(results.isEmpty())
  }
  @Test fun confirmReadsClockAgainWithoutWaitingForRecomposition() {
    show()
    compose.onNodeWithText("Tomorrow").performClick()
    compose.runOnIdle { clock.value = next.plusSeconds(1) }
    compose.onNodeWithText("Conferma").performClick()
    assertTrue(results.isEmpty())
    compose.onNodeWithText("Conferma").assertIsNotEnabled()
  }
  @Test fun fullDateSelectedAndTimeIsAdjustable() {
    show(custom = true)
    val selected = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
    compose.onNode(selected).assertExists()
    assertTrue(compose.onNode(selected).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single().contains("2026"))
    compose.onNodeWithContentDescription("Ora", useUnmergedTree = true).performScrollTo()
      .performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
    compose.onNodeWithContentDescription("Ora", useUnmergedTree = true)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "15"))
  }
  @Test fun largeDarkReducedEnglishKeepsFooterAndDayTargetsUsable() {
    show(custom = true, large = true, english = true)
    compose.onNodeWithText("Confirm").assertIsDisplayed()
    compose.onNodeWithText("Cancel").assertIsDisplayed()
    val selected = compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    selected.performScrollTo()
    val bounds = selected.fetchSemanticsNode().boundsInRoot
    val label = compose.onNodeWithText("9", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    assertTrue(bounds.contains(label.topLeft) && bounds.contains(label.bottomRight))
    selected.assertWidthIsAtLeast(48.dp)
  }
  @Test fun gapRejectsAndOverlapRequiresAnOffset() {
    clock.value = Instant.parse("2026-01-01T00:00:00Z")
    show(custom = true, initial = Instant.parse("2026-03-29T00:30:00Z"))
    compose.onNodeWithContentDescription("Ora", useUnmergedTree = true).performScrollTo()
      .performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
    compose.onNodeWithText(FluidDateTimeLabels().gap).performScrollTo().assertIsDisplayed()
    compose.onNodeWithText("Conferma").assertIsNotEnabled()
  }
  @Test fun overlapOffsetChangesTheAbsoluteResult() {
    clock.value = Instant.parse("2026-01-01T00:00:00Z")
    show(custom = true, initial = Instant.parse("2026-10-25T00:30:00Z"))
    compose.onNodeWithText("Conferma").assertIsNotEnabled()
    compose.onNodeWithText("UTC+01:00").performScrollTo().performClick()
    compose.onNodeWithText("Conferma").performClick()
    assertEquals(Instant.parse("2026-10-25T01:30:00Z"), results.single().instant)
  }

  @Test fun backCancelsTheSheet() {
    show()
    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals(1, cancelled)
    assertTrue(results.isEmpty())
  }
  @Test fun outOfBoundsQuickOptionCannotBeSelected() {
    show(min = next.plusSeconds(60))
    compose.onNodeWithText("Tomorrow").assertIsNotEnabled().performClick()
    compose.onNodeWithText("Conferma").assertIsNotEnabled()
    assertTrue(results.isEmpty())
  }
  @Test fun selectedTimeSurvivesSavedStateRestore() {
    show(custom = true)
    compose.onNodeWithContentDescription("Ora", useUnmergedTree = true).performScrollTo()
      .performSemanticsAction(SemanticsActions.SetProgress) { it(15f) }
    restoration.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    compose.onNodeWithContentDescription("Ora", useUnmergedTree = true)
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "15"))
  }

}
