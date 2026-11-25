package dev.antigravity.fluidengine.account.prova.tema

import android.app.Application
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dev.antigravity.fluidengine.account.AccountClientConfig
import dev.antigravity.fluidengine.account.PampaAccount
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.storage.EngineSettingsStore
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassPreference
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.launch

private const val TAG = "PampaProvaTema"
private const val STORE = "dev.antigravity.fluidengine.account.prova.store"

/**
 * Una app finta: cerca lo Store finto al posto di Pampa Store, e per il resto fa quello che farà ogni app Pampa con
 * `engine-account`, cioè niente: `EngineSettingsStore` prende da solo il tema comune, e l'icona cambia da sola.
 */
class ProvaApp : Application() {
  override fun onCreate() {
    super.onCreate()
    PampaAccount.configure(AccountClientConfig(storePackage = STORE, devStorePackage = STORE, devBuild = false))
    // Il primo contatto con lo Store: la copia locale, poi la lettura. L'icona si mette in pari da qui.
    PampaAccount.client(this)
    Log.i(TAG, "$packageName avviata, icona ${PampaAccount.icons(this).currentIconSet()}")
  }
}

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    val store = EngineSettingsStore(this)
    setContent {
      val settings by store.settings.collectAsState(initial = null)
      val s = settings ?: return@setContent
      LaunchedEffect(s) { Log.i(TAG, "$packageName tema ${s.themeMode} accento ${s.accentMode}/${s.customAccentName} vetro ${s.glassPreference} testo ${s.textScale}") }
      FluidTheme(s) {
        var icon by remember { mutableStateOf("") }
        LaunchedEffect(s) { icon = describeIcon() }
        Column(
          Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(24.dp),
          verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
          Text(getString(R.string.nome_app), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
          Text(describe(s), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
          Text("Vetro (cursore): ${LocalFluidGlassPreference.current}", color = MaterialTheme.colorScheme.onBackground)
          Text(icon, color = MaterialTheme.colorScheme.onBackground)
          FluidButton(text = "Fissa una scorciatoia", onClick = ::pinShortcut)
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    lifecycleScope.launch { PampaAccount.refresh(this@MainActivity) }
  }

  private fun describe(s: EngineSettings) =
    "Tema: ${s.themeMode} · accento: ${s.accentMode} ${s.customAccentName} · testo ×${s.textScale} · contrasto alto: ${s.highContrast}"

  private fun describeIcon(): String {
    val icons = PampaAccount.icons(this)
    return "Icona: ${icons.currentIconSet()}" + (icons.pending?.let { " (cambia in sottofondo in «$it»)" } ?: "")
  }

  /** Una scorciatoia fissata sulla Home: è quello che alcuni launcher tolgono quando l'icona cambia. */
  private fun pinShortcut() {
    val manager = getSystemService(ShortcutManager::class.java)
    if (manager == null || !manager.isRequestPinShortcutSupported) {
      Log.i(TAG, "$packageName scorciatoie fissate non supportate")
      return
    }
    val info = ShortcutInfo.Builder(this, "prova-fissata")
      .setShortLabel(getString(R.string.nome_app) + " ·")
      .setIcon(Icon.createWithResource(this, R.mipmap.ic_default))
      .setIntent(Intent(Intent.ACTION_MAIN).setClassName(this, MainActivity::class.java.name))
      .build()
    val asked = manager.requestPinShortcut(info, null)
    Log.i(TAG, "$packageName scorciatoia chiesta: $asked")
  }
}
