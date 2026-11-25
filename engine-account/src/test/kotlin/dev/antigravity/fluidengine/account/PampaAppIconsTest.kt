package dev.antigravity.fluidengine.account

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** F10.8 · Le icone con `activity-alias` (critica C6): le app chiuse cambiano subito, quelle aperte al sottofondo. */
@RunWith(RobolectricTestRunner::class)
class PampaAppIconsTest {

  /** Il sistema finto: quattro alias, l'app aperta o chiusa, e il sottofondo che si chiama a mano. */
  private class FakeHost(var foreground: Boolean = false) : PampaAppIcons.Host {
    val aliases = listOf(
      PampaIconAlias("app.IconaDiSerie", "default", enabledInManifest = true),
      PampaIconAlias("app.IconaTinta", "tinted", enabledInManifest = false),
      PampaIconAlias("app.IconaScura", "dark", enabledInManifest = false),
    )
    val enabled = HashMap<String, Boolean>()
    val log = ArrayList<String>()
    var background: (() -> Unit)? = null
    var listens = 0

    override fun aliases() = aliases
    override fun isEnabled(alias: PampaIconAlias) = enabled[alias.className] ?: alias.enabledInManifest
    override fun setEnabled(alias: PampaIconAlias, enabled: Boolean) {
      this.enabled[alias.className] = enabled
      log += "${if (enabled) "+" else "-"}${alias.iconSet}"
    }
    override fun inForeground() = foreground
    override fun listenBackground(callback: () -> Unit) {
      listens++
      background = callback
    }
    fun on() = aliases.filter(::isEnabled).map { it.iconSet }
  }

  @Test
  fun `un'app chiusa cambia subito, prima accende il nuovo e poi spegne il vecchio`() {
    val host = FakeHost(foreground = false)
    val icons = PampaAppIcons(host)
    assertEquals(PampaIconOutcome.APPLIED, icons.request("tinted"))
    assertEquals(listOf("tinted"), host.on())
    assertEquals(listOf("+tinted", "-default"), host.log)
    assertEquals(PampaIconOutcome.UNCHANGED, icons.request("tinted"))
    assertEquals("tinted", icons.currentIconSet())
  }

  @Test
  fun `un'app aperta aspetta il sottofondo, e vale l'ultima scelta`() {
    val host = FakeHost(foreground = true)
    val icons = PampaAppIcons(host)
    assertEquals(PampaIconOutcome.DEFERRED, icons.request("tinted"))
    assertEquals(PampaIconOutcome.DEFERRED, icons.request("dark"))
    assertEquals(listOf("default"), host.on())
    assertEquals("dark", icons.pending)
    assertEquals(1, host.listens)
    host.foreground = false
    host.background!!.invoke()
    assertEquals(listOf("dark"), host.on())
    assertNull(icons.pending)
    // Un secondo sottofondo senza niente in attesa non tocca niente.
    host.log.clear()
    host.background!!.invoke()
    assertTrue(host.log.isEmpty())
  }

  @Test
  fun `tornare all'icona di serie prima del sottofondo annulla l'attesa`() {
    val host = FakeHost(foreground = true)
    val icons = PampaAppIcons(host)
    icons.request("tinted")
    assertEquals(PampaIconOutcome.UNCHANGED, icons.request(null))
    assertNull(icons.pending)
    host.background!!.invoke()
    assertEquals(listOf("default"), host.on())
  }

  @Test
  fun `una famiglia che l'app non ha vale l'icona di serie, e senza alias non si fa niente`() {
    val host = FakeHost()
    val icons = PampaAppIcons(host)
    icons.request("dark")
    assertEquals(PampaIconOutcome.APPLIED, icons.request("clear"))
    assertEquals(listOf("default"), host.on())
    val none = PampaAppIcons(object : PampaAppIcons.Host by host {
      override fun aliases() = emptyList<PampaIconAlias>()
    })
    assertFalse(none.hasAliases)
    assertEquals(PampaIconOutcome.NO_ALIASES, none.request("tinted"))
  }

  @Test
  fun `col PackageManager vero - gli alias si trovano dal meta-data e si accendono con DONT_KILL_APP`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val pm = app.packageManager
    fun alias(name: String, set: String, enabled: Boolean) = ActivityInfo().apply {
      packageName = app.packageName
      this.name = name
      targetActivity = "$name.Target"
      this.enabled = enabled
      metaData = Bundle().apply { putString(AccountProtocol.META_ICON_SET, set) }
      applicationInfo = app.applicationInfo
    }
    val plain = ActivityInfo().apply { packageName = app.packageName; name = "app.Altra"; applicationInfo = app.applicationInfo }
    val info = PackageInfo().apply {
      packageName = app.packageName
      applicationInfo = ApplicationInfo(app.applicationInfo)
      activities = arrayOf(alias("app.IconaDiSerie", "default", true), alias("app.IconaTinta", "tinted", false), plain)
    }
    shadowOf(pm).installPackage(info)
    val host = SystemIconHost(app)
    val aliases = host.aliases()
    assertEquals(listOf("default", "tinted"), aliases.map { it.iconSet })
    // L'importanza del processo in Robolectric non è quella di un telefono: qui l'app è chiusa per costruzione.
    val icons = PampaAppIcons(object : PampaAppIcons.Host by host {
      override fun inForeground() = false
    })
    icons.request("tinted")
    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_ENABLED, pm.getComponentEnabledSetting(ComponentName(app.packageName, "app.IconaTinta")))
    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DISABLED, pm.getComponentEnabledSetting(ComponentName(app.packageName, "app.IconaDiSerie")))
    // Tornare all'icona di serie riporta i due alias allo stato del manifest: nessuna traccia.
    icons.request(null)
    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(ComponentName(app.packageName, "app.IconaTinta")))
    assertEquals(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, pm.getComponentEnabledSetting(ComponentName(app.packageName, "app.IconaDiSerie")))
  }

  @Test
  fun `il sottofondo vero e TRIM_MEMORY_UI_HIDDEN`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    var calls = 0
    SystemIconHost(app).listenBackground { calls++ }
    @Suppress("DEPRECATION")
    app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW)
    assertEquals(0, calls)
    app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
    assertEquals(1, calls)
  }
}
