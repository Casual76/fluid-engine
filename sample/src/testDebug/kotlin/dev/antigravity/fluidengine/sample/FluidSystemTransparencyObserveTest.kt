@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample

import android.content.Context
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.GlassMaterialAccessibility
import dev.antigravity.fluidengine.ui.fluid.LocalGlassSystemReduceTransparency
import dev.antigravity.fluidengine.ui.fluid.glassReduceTransparencyKeys
import dev.antigravity.fluidengine.ui.fluid.rememberGlassMaterialAccessibility
import dev.antigravity.fluidengine.ui.glass.field.GlassRecipe
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowBuild

/**
 * F6-e: «Riduci trasparenza e sfocatura» di Samsung (Accessibilità → Ausili visivi) arriva al vetro, all'avvio e a caldo.
 *
 * Sul Tab S9 (One UI 8, API 36) l'interruttore scrive `Settings.Global.accessibility_reduce_transparency`, e solo quella
 * (S5d e la diagnosi di F6-corr). La chiave è leggibile dalle app (`@Readable` in `framework.jar`). In S5d il vetro di
 * Foto restava identico al pixel: l'interruttore sta nelle Impostazioni, l'app è in sottofondo quando cambia, e la
 * notifica dell'osservatore a un'app congelata può non arrivare. Per questo il tema rilegge anche quando l'Activity
 * riprende. Qui la stessa sequenza: il cambio con la notifica, e il cambio **senza** notifica seguito da una ripresa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h640dp-xhdpi")
class FluidSystemTransparencyObserveTest {

  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val key = "accessibility_reduce_transparency"
  private val resolver get() = ApplicationProvider.getApplicationContext<Context>().contentResolver

  @Before fun samsung() {
    ShadowBuild.setManufacturer("samsung")
    Settings.Global.putInt(resolver, key, 0)
    Settings.Global.putInt(resolver, "disable_window_blurs", 0)
  }

  @After fun reset() {
    Settings.Global.putInt(resolver, key, 0)
  }

  @Test fun `le chiavi sono quella AOSP da API 31 e quella Samsung solo su Samsung`() {
    assertEquals(listOf("disable_window_blurs", key), glassReduceTransparencyKeys(36, "samsung"))
    assertEquals(listOf("disable_window_blurs", key), glassReduceTransparencyKeys(36, "Samsung"))
    assertEquals(listOf("disable_window_blurs"), glassReduceTransparencyKeys(36, "Google"))
    assertEquals(listOf(key), glassReduceTransparencyKeys(30, "samsung"))
    assertEquals(emptyList<String>(), glassReduceTransparencyKeys(30, null))
  }

  private var system = false
  private var material = GlassMaterialAccessibility.Default

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        system = LocalGlassSystemReduceTransparency.current
        material = rememberGlassMaterialAccessibility().value
      }
    }
    compose.waitForIdle()
  }

  @Test fun `all'avvio il tema legge la trasparenza ridotta di Samsung e il materiale la riceve`() {
    Settings.Global.putInt(resolver, key, 1)
    show()
    assertTrue("il tema non ha letto la chiave Samsung", system)
    assertTrue("il materiale non l'ha ricevuta", material.reduceTransparency)
  }

  @Test fun `a caldo con la notifica dell'osservatore il vetro cambia, e torna`() {
    show()
    assertFalse(system)
    compose.runOnUiThread {
      Settings.Global.putInt(resolver, key, 1)
      resolver.notifyChange(Settings.Global.getUriFor(key), null)
    }
    compose.waitForIdle()
    assertTrue("acceso a caldo", system && material.reduceTransparency)
    compose.runOnUiThread {
      Settings.Global.putInt(resolver, key, 0)
      resolver.notifyChange(Settings.Global.getUriFor(key), null)
    }
    compose.waitForIdle()
    assertFalse("spento a caldo", system || material.reduceTransparency)
  }

  @Test fun `un cambio senza notifica mentre l'app e' in sottofondo si vede alla ripresa`() {
    show()
    assertFalse(system)
    // L'utente va nelle Impostazioni: l'Activity si ferma.
    compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    // L'interruttore cambia, e la notifica all'app congelata va persa: nessun `notifyChange`.
    Settings.Global.putInt(resolver, key, 1)
    // Torna all'app.
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    compose.waitForIdle()
    assertTrue("alla ripresa il tema doveva rileggere la chiave", system && material.reduceTransparency)

    compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    Settings.Global.putInt(resolver, key, 0)
    compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    compose.waitForIdle()
    assertFalse("e anche quando la si rispegne", system || material.reduceTransparency)
  }

  @Test fun `con la trasparenza ridotta il velo del vetro arriva almeno al 96 per cento, chiaro e scuro`() {
    for (dark in listOf(false, true)) {
      val normal = glassRecipe(GlassRole.Floating, 44f, 44f, darkSide = dark, out = GlassRecipe())
      val reduced = glassRecipe(
        GlassRole.Floating, 44f, 44f, darkSide = dark, out = GlassRecipe(),
        accessibility = GlassMaterialAccessibility(reduceTransparency = true),
      )
      assertTrue("dark=$dark: il velo di serie ${normal.veilColor.alpha} è già pieno?", normal.veilColor.alpha < 0.9f)
      assertTrue("dark=$dark: velo ridotto ${reduced.veilColor.alpha}", reduced.veilColor.alpha >= 0.96f)
      assertEquals("dark=$dark: il velo ridotto non segue lo sfondo", 0f, reduced.veilAdapt, 0f)
      assertTrue("dark=$dark: il velo resta un colore", reduced.veilColor != Color.Unspecified)
    }
  }
}
