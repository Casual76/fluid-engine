package dev.antigravity.fluidengine.aiui

import androidx.compose.foundation.lazy.LazyListScope
import dev.antigravity.fluidengine.aiui.settings.*
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class FluidAiSettingsPartsTest {
  private fun keys(parts: Set<FluidAiSettingsPart>?, slots: FluidAiSettingsSlots = FluidAiSettingsSlots()): List<String> {
    val keys = mutableListOf<String>()
    val scope = Proxy.newProxyInstance(LazyListScope::class.java.classLoader, arrayOf(LazyListScope::class.java)) { _, method, args ->
      if (method.name == "item") keys += args[0].toString()
      null
    } as LazyListScope
    val actions = FluidAiSettingsActions(saveAndVerify = { _, _ -> error("non invocato") }, removeKey = {}, setEnabled = {})
    if (parts == null) scope.fluidAiSettingsSection(FluidAiSettingsState(), actions, slots = slots)
    else scope.fluidAiSettingsSection(FluidAiSettingsState(), actions, slots = slots, parts = parts)
    return keys
  }
  @Test fun `il filtro omesso conserva tutti i gruppi`() {
    assertEquals(keys(FluidAiSettingsPart.entries.toSet()), keys(null))
    assertTrue(keys(null).contains("fluid-ai-keys"))
  }
  @Test fun `un solo gruppo non duplica gli altri e conserva gli slot`() {
    val seen = mutableListOf<String>()
    val slots = FluidAiSettingsSlots(beforeKeys = { seen += "prima" }, afterModels = { seen += "modelli" }, footer = { seen += "fine" })
    assertEquals(listOf("fluid-ai-enabled"), keys(setOf(FluidAiSettingsPart.Enabled), slots))
    assertEquals(listOf("prima", "modelli", "fine"), seen)
    assertTrue(keys(emptySet()).isEmpty())
  }
}
