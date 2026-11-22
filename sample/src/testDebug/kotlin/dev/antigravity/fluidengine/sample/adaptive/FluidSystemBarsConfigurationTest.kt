package dev.antigravity.fluidengine.sample.adaptive

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Un'Activity come quelle delle app: bordo a bordo con `enableEdgeToEdge()`, e il tema dell'engine con [ExtraTheme]. */
class FluidBarsProbeActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val mode = ThemeMode.valueOf(intent.getStringExtra(ExtraTheme) ?: ThemeMode.DARK.name)
    setContent {
      FluidTheme(settings = remember { EngineSettings(themeMode = mode) }) { Box(Modifier.fillMaxSize()) }
    }
  }

  companion object {
    const val ExtraTheme = "tema"
  }
}

/**
 * Le barre di sistema dopo un cambio di configurazione (F6B.12, riprese del 22 novembre 2026: la galleria in tema scuro,
 * ruotata sul Tab S9 in tema chiaro, aveva l'ora scura sul fondo scuro e una fascia chiara sotto la maniglia dei gesti).
 *
 * La causa: `enableEdgeToEdge()` di androidx.activity 1.13 aggiunge alla finestra una vista che, a ogni cambio di
 * configurazione, rimette lo stile delle barre secondo il tema del **sistema**; un'Activity che gestisce da sé la
 * rotazione (`configChanges`, come la galleria) non rinasce, e `FluidTheme` non le rimetteva. Qui la rotazione arriva
 * come la manda il sistema a un'Activity così, in un messaggio solo: prima l'Activity, poi la finestra alle sue viste
 * (quella di `enableEdgeToEdge()` e quella di Compose). Poi la stessa configurazione mandata di nuovo (Compose non vede
 * niente di nuovo, la vista di `enableEdgeToEdge()` rimette comunque il suo stile), e la ricreazione, l'altra strada.
 *
 * Si controllano `isAppearanceLightStatusBars` e `isAppearanceLightNavigationBars`: devono seguire il tema dell'app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-port-xhdpi")
class FluidSystemBarsConfigurationTest {

  private fun idle() = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))

  /** Le Activity create, da distruggere alla fine: la suite del sample gira in un processo solo, con poca memoria. */
  private val controllers = ArrayList<ActivityController<FluidBarsProbeActivity>>()

  @After fun stop() {
    for (controller in controllers) runCatching { controller.pause().stop().destroy() }
    controllers.clear()
    idle()
  }

  private fun launch(mode: ThemeMode): ActivityController<FluidBarsProbeActivity> {
    val intent = Intent().putExtra(FluidBarsProbeActivity.ExtraTheme, mode.name)
    return Robolectric.buildActivity(FluidBarsProbeActivity::class.java, intent).setup().also {
      controllers += it
      idle()
    }
  }

  /** Le icone scure sulla barra di stato e su quella di navigazione (cioè le barre «chiare»). */
  private fun bars(activity: Activity): Pair<Boolean, Boolean> =
    WindowCompat.getInsetsController(activity.window, activity.window.decorView).let {
      it.isAppearanceLightStatusBars to it.isAppearanceLightNavigationBars
    }

  /** La configurazione che l'Activity ha adesso (le risorse del test non la seguono da sole). */
  private var current: Configuration? = null

  /** Un cambio di configurazione gestito dall'Activity, in un messaggio solo: l'Activity, poi le viste della finestra. */
  private fun deliver(activity: Activity, config: Configuration) {
    activity.onConfigurationChanged(config)
    activity.window.decorView.dispatchConfigurationChanged(config)
    current = config
    idle()
  }

  /** Una rotazione: da verticale a orizzontale o indietro, larghezza e altezza scambiate. */
  private fun rotate(activity: Activity) {
    val config = Configuration(current ?: activity.resources.configuration).apply {
      orientation = if (orientation == Configuration.ORIENTATION_LANDSCAPE) Configuration.ORIENTATION_PORTRAIT
      else Configuration.ORIENTATION_LANDSCAPE
      val width = screenWidthDp
      screenWidthDp = screenHeightDp
      screenHeightDp = width
    }
    deliver(activity, config)
  }

  private fun followsTheme(mode: ThemeMode, light: Boolean) {
    val controller = launch(mode)
    val activity = controller.get()
    val expected = light to light
    assertEquals("$mode all'avvio", expected, bars(activity))
    rotate(activity)
    assertEquals("$mode dopo una rotazione", expected, bars(activity))
    rotate(activity)
    assertEquals("$mode dopo la seconda rotazione", expected, bars(activity))
    deliver(activity, Configuration(current))
    assertEquals("$mode dopo la stessa configurazione mandata di nuovo", expected, bars(activity))
    controller.recreate()
    idle()
    assertEquals("$mode dopo la ricreazione", expected, bars(controller.get()))
  }

  @Test fun `telefono - app scura su sistema chiaro, le barre restano scure dopo la rotazione`() =
    followsTheme(ThemeMode.DARK, light = false)

  @Test @Config(qualifiers = "w360dp-h800dp-port-night-xhdpi")
  fun `telefono - app chiara su sistema scuro, le barre restano chiare dopo la rotazione`() =
    followsTheme(ThemeMode.LIGHT, light = true)

  @Test @Config(qualifiers = "w753dp-h1205dp-port-xhdpi")
  fun `tablet - app scura su sistema chiaro`() = followsTheme(ThemeMode.DARK, light = false)

  @Test @Config(qualifiers = "w753dp-h1205dp-port-night-xhdpi")
  fun `tablet - app chiara su sistema scuro`() = followsTheme(ThemeMode.LIGHT, light = true)

  @Test fun `app che segue il sistema - chiaro su chiaro`() = followsTheme(ThemeMode.SYSTEM, light = true)

  @Test @Config(qualifiers = "w673dp-h841dp-port-night-xhdpi")
  fun `pieghevole - app che segue il sistema, scuro su scuro`() = followsTheme(ThemeMode.SYSTEM, light = false)
}
