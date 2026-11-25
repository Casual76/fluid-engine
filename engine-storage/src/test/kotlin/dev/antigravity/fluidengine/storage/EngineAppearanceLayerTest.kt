package dev.antigravity.fluidengine.storage

import dev.antigravity.fluidengine.foundation.AccentMode
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** F10.8 · L'ultimo gradino della regola di chi vince: le scelte comuni sopra le impostazioni dell'app. */
class EngineAppearanceLayerTest {

  private val own = EngineSettings(
    themeMode = ThemeMode.LIGHT,
    accentMode = AccentMode.BRAND,
    customAccentName = "jade",
    hapticsEnabled = false,
  )

  @Test
  fun `senza scelte comuni restano le impostazioni dell'app`() {
    assertTrue(EngineAppearanceLayer.EMPTY.isEmpty)
    assertEquals(own, EngineAppearanceLayer.EMPTY.applyTo(own))
    // L'icona non è del tema: una famiglia d'icone da sola non cambia niente del tema.
    assertEquals(own, EngineAppearanceLayer(iconSet = "tinted").applyTo(own))
  }

  @Test
  fun `il comune vince sui valori dell'app, campo per campo, e il resto resta dell'app`() {
    val s = EngineAppearanceLayer(themeMode = ThemeMode.DARK, accent = "#ff9500", glassPreference = 0.8f, textScale = 1.2f, highContrast = true, reducedMotion = true).applyTo(own)
    assertEquals(ThemeMode.DARK, s.themeMode)
    assertEquals(AccentMode.CUSTOM_PRESET, s.accentMode)
    assertEquals("#ff9500", s.customAccentName)
    assertEquals(0.8f, s.glassPreference)
    assertEquals(1.2f, s.textScale)
    assertTrue(s.highContrast)
    assertTrue(s.reducedMotion)
    // L'aptica non è nel tema comune: resta quella dell'app.
    assertFalse(s.hapticsEnabled)
  }

  @Test
  fun `l'accento comune - un colore, il colore di ogni app, i colori del sistema`() {
    val chosen = own.copy(accentMode = AccentMode.CUSTOM_PRESET, customAccentName = "jade", dynamicColorEnabled = false)
    assertEquals(AccentMode.BRAND, EngineAppearanceLayer(accent = EngineAppearanceLayer.ACCENT_APP).applyTo(chosen).accentMode)
    val system = EngineAppearanceLayer(accent = EngineAppearanceLayer.ACCENT_SYSTEM).applyTo(chosen)
    assertEquals(AccentMode.DYNAMIC, system.accentMode)
    assertTrue(system.dynamicColorEnabled)
    assertEquals("#34c759", EngineAppearanceLayer(accent = "#34c759").applyTo(own).customAccentName)
  }

  @Test
  fun `uno scuro comune non toglie il nero vero a chi l'aveva`() {
    val amoled = EngineSettings(themeMode = ThemeMode.AMOLED)
    val dark = EngineAppearanceLayer(themeMode = ThemeMode.DARK).applyTo(amoled)
    assertEquals(ThemeMode.DARK, dark.themeMode)
    assertTrue(dark.amoledEnabled)
    val light = EngineAppearanceLayer(themeMode = ThemeMode.LIGHT).applyTo(amoled)
    assertEquals(ThemeMode.LIGHT, light.themeMode)
  }

  @Test
  fun `un comune che dice standard spegne il contrasto alto dell'app`() {
    val s = EngineAppearanceLayer(highContrast = false, reducedMotion = false).applyTo(own.copy(highContrast = true, reducedMotion = true))
    assertFalse(s.highContrast)
    assertFalse(s.reducedMotion)
  }

  @Test
  fun `la sorgente installata a mano vince su quella trovata, e si toglie`() {
    val fake = object : EngineSharedAppearance {
      override fun layer(context: android.content.Context) = kotlinx.coroutines.flow.flowOf(null)
      override suspend fun setException(context: android.content.Context, followShared: Boolean, exception: EngineAppearanceLayer?) = Unit
    }
    try {
      EngineSharedAppearance.install(fake)
      assertSame(fake, EngineSharedAppearance.current())
      EngineSharedAppearance.install(null)
      assertNull(EngineSharedAppearance.current())
    } finally {
      EngineSharedAppearance.reset()
    }
    // In questo modulo nessuno dichiara una sorgente: senza engine-account l'app ha il suo tema.
    assertNull(EngineSharedAppearance.current())
  }
}
