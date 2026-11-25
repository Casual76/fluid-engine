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
      "appearance.glass" to "tinted",
      "appearance.textScale" to "1.25",
      "appearance.contrast" to "high",
      "motion.reduced" to "true",
      "icon.set" to "dark",
    )))
    assertEquals(ThemeMode.DARK, l.themeMode)
    assertEquals("#ff9500", l.accent)
    assertEquals(0.8f, l.glassPreference)
    assertEquals(1.25f, l.textScale)
    assertEquals(true, l.highContrast)
    assertEquals(true, l.reducedMotion)
    assertEquals("dark", l.iconSet)
    assertTrue(l.followShared)
  }

  @Test
  fun `prima l'eccezione dell'app, poi il comune - ma solo se l'app non segue il comune`() {
    val global = mapOf("appearance.mode" to "dark", "appearance.glass" to "clear", "icon.set" to "tinted")
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
  fun `l'accento - il colore di ogni app e i colori del sistema si capiscono gia, anche se il server non li scrive ancora`() {
    assertEquals("app", PampaAppearance.layerOf(shared(mapOf("appearance.accent" to "app"))).accent)
    assertEquals("system", PampaAppearance.layerOf(shared(mapOf("appearance.accent" to "system"))).accent)
    assertEquals(mapOf("appearance.accent" to "app"), PampaAppearance.exceptionValues(EngineAppearanceLayer(accent = "app")))
    assertEquals(emptyMap<String, String>(), PampaAppearance.exceptionValues(EngineAppearanceLayer(accent = "arcobaleno")))
  }

  @Test
  fun `il vetro - trasparente e il vetro di oggi, colorato e ridotto piu pieni, e un numero passa`() {
    assertEquals(0.5f, PampaAppearance.glassLevel("clear"))
    assertEquals(0.8f, PampaAppearance.glassLevel("tinted"))
    assertEquals(1f, PampaAppearance.glassLevel("reduced"))
    assertEquals(0.3f, PampaAppearance.glassLevel("0.3"))
    assertNull(PampaAppearance.glassLevel("1.5"))
  }

  @Test
  fun `l'eccezione scritta dall'app torna nelle chiavi che un'app puo tenere`() {
    val v = PampaAppearance.exceptionValues(EngineAppearanceLayer(
      themeMode = ThemeMode.AMOLED, accent = "#34C759", glassPreference = 0.9f, textScale = 1.4f, highContrast = true, iconSet = "clear",
    ))
    // Il testo e il contrasto sono solo comuni (`scopes: global`): un'app non li tiene diversi.
    assertEquals(mapOf("appearance.mode" to "dark", "appearance.accent" to "#34c759", "appearance.glass" to "tinted", "icon.set" to "clear"), v)
    assertEquals(PampaAppearance.APP_KEYS, setOf("appearance.mode", "appearance.accent", "appearance.glass", "icon.set"))
  }
}
