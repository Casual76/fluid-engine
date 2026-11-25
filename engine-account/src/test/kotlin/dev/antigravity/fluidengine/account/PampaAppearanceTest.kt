package dev.antigravity.fluidengine.account

import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.storage.EngineAppearanceLayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** F10.8 · L'aspetto dello Store tradotto per il tema, e i primi due gradini della regola di chi vince. */
class PampaAppearanceTest {

  private fun shared(global: Map<String, String>, override: Map<String, String>? = null, follow: Boolean = true) =
    SharedAppearance(1, global, override, follow)

  @Test
  fun `il comune arriva al tema con i valori del contratto`() {
    val l = PampaAppearance.layerOf(shared(mapOf(
      "appearance.mode" to "dark",
      "appearance.accent" to "#FF9500",
      "appearance.glass" to "0.4",
      "appearance.textScale" to "1.25",
      "appearance.contrast" to "high",
      "motion.reduced" to "true",
      "icon.set" to "dark",
    )))
    assertEquals(ThemeMode.DARK, l.themeMode)
    assertEquals("#ff9500", l.accent)
    assertEquals(0.2f, l.glassPreference!!, 1e-6f)
    assertEquals(1.25f, l.textScale)
    assertEquals(true, l.highContrast)
    assertEquals(true, l.reducedMotion)
    assertEquals("dark", l.iconSet)
    assertTrue(l.followShared)
  }

  @Test
  fun `prima l'eccezione dell'app, poi il comune - ma solo se l'app non segue il comune`() {
    val global = mapOf("appearance.mode" to "dark", "appearance.glass" to "1", "icon.set" to "tinted")
    val exception = mapOf("appearance.mode" to "light")
    // L'app ha la sua eccezione: il modo è suo, il resto (vetro, icone) segue il comune.
    val own = PampaAppearance.layerOf(shared(global, exception, follow = false))
    assertEquals(ThemeMode.LIGHT, own.themeMode)
    assertEquals(0.5f, own.glassPreference)
    assertEquals("tinted", own.iconSet)
    assertFalse(own.followShared)
    // «Segui Pampa Store»: l'eccezione salvata resta nello Store, ma non vale.
    assertEquals(ThemeMode.DARK, PampaAppearance.layerOf(shared(global, exception, follow = true)).themeMode)
  }

  @Test
  fun `i valori che non si capiscono valgono nessuna scelta - l'app resta com'era su quel campo`() {
    val l = PampaAppearance.layerOf(shared(mapOf(
      "appearance.mode" to "sepia",
      "appearance.accent" to "rosso",
      "appearance.glass" to "liquid",
      "appearance.textScale" to "3",
      "appearance.contrast" to "medio",
      "motion.reduced" to "forse",
      "icon.set" to "neon",
      "chiave.nuova" to "x",
    )))
    assertEquals(EngineAppearanceLayer(followShared = true), l)
  }

  @Test
  fun `l'accento - il colore di ogni app e i colori del sistema`() {
    assertEquals("app", PampaAppearance.layerOf(shared(mapOf("appearance.accent" to "app"))).accent)
    assertEquals("system", PampaAppearance.layerOf(shared(mapOf("appearance.accent" to "system"))).accent)
    assertEquals(mapOf("appearance.accent" to "app"), PampaAppearance.exceptionValues(EngineAppearanceLayer(accent = "app")))
    assertEquals(emptyMap<String, String>(), PampaAppearance.exceptionValues(EngineAppearanceLayer(accent = "arcobaleno")))
  }

  @Test
  fun `il vetro - la chiarezza da 0 a 1 e la meta trasparente del cursore dell'engine`() {
    // 1, «più sfocato, come ora», è il vetro di serie al bit.
    assertEquals(PampaAppearance.GLASS_DEFAULT, PampaAppearance.glassPreferenceOf("1"))
    assertEquals(0f, PampaAppearance.glassPreferenceOf("0"))
    assertEquals(0.175f, PampaAppearance.glassPreferenceOf("0.35")!!, 1e-6f)
    // Gli elenchi di prima del contratto non valgono più, e fuori corsa non vale.
    assertNull(PampaAppearance.glassPreferenceOf("tinted"))
    assertNull(PampaAppearance.glassPreferenceOf("1.5"))
    assertNull(PampaAppearance.glassPreferenceOf("-0.1"))
    // All'indietro: un valore oltre il vetro di serie (la metà colorata di prima) è 1, come «come ora».
    assertEquals(1f, PampaAppearance.clarityOf(0.9f), 0f)
    assertEquals(0.4f, PampaAppearance.clarityOf(0.2f), 1e-6f)
    assertEquals(1f, PampaAppearance.clarityOf(Float.NaN), 0f)
  }

  @Test
  fun `l'eccezione scritta dall'app torna nelle chiavi che un'app puo tenere`() {
    val v = PampaAppearance.exceptionValues(EngineAppearanceLayer(
      themeMode = ThemeMode.AMOLED, accent = "#34C759", glassPreference = 0.9f, textScale = 1.4f, highContrast = true, iconSet = "clear",
    ))
    // Il testo e il contrasto sono solo comuni (`scopes: global`): un'app non li tiene diversi.
    assertEquals(mapOf("appearance.mode" to "dark", "appearance.accent" to "#34c759", "appearance.glass" to "1", "icon.set" to "clear"), v)
    assertEquals("0.35", PampaAppearance.exceptionValues(EngineAppearanceLayer(glassPreference = 0.175f))["appearance.glass"])
    assertEquals(PampaAppearance.APP_KEYS, setOf("appearance.mode", "appearance.accent", "appearance.glass", "icon.set"))
  }
}
