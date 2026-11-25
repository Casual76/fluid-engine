package dev.antigravity.fluidengine.account

import android.app.ActivityManager
import android.content.ComponentCallbacks2
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

/** Un'icona dell'app: un `<activity-alias>` col meta-data [AccountProtocol.META_ICON_SET]. */
data class PampaIconAlias(
  /** Il nome completo della classe dell'alias, come nel manifest risolto. */
  val className: String,
  /** La famiglia che porta: `default`, `tinted`, `dark` o `clear`. */
  val iconSet: String,
  /** È acceso nel manifest (`android:enabled`, di serie vero)? Quello acceso è l'icona di serie dell'app. */
  val enabledInManifest: Boolean,
)

/** Cosa è successo a una richiesta d'icona. */
enum class PampaIconOutcome {
  /** L'app non ha alias d'icona: niente da fare. */
  NO_ALIASES,
  /** L'icona è già quella. */
  UNCHANGED,
  /** Cambiata adesso: l'app non era in primo piano. */
  APPLIED,
  /** L'app è aperta: cambia appena passa in sottofondo (critica C6). */
  DEFERRED,
}

/**
 * F10.8 · L'icona dell'app che segue la famiglia d'icone dell'aspetto comune (`icon.set`; critica C6;
 * `b\architettura.md` § 5.2). L'app dichiara un `<activity-alias>` per famiglia, ognuno col meta-data
 * `dev.pampa.account.ICON_SET`, e uno solo acceso nel manifest (l'icona di serie):
 *
 * ```xml
 * <activity-alias android:name=".IconaDiSerie" android:targetActivity=".MainActivity" android:icon="@mipmap/ic_launcher"
 *     android:exported="true">
 *   <intent-filter> <action android:name="android.intent.action.MAIN" /> <category android:name="android.intent.category.LAUNCHER" /> </intent-filter>
 *   <meta-data android:name="dev.pampa.account.ICON_SET" android:value="default" />
 * </activity-alias>
 * <activity-alias android:name=".IconaTinta" android:enabled="false" … >  … android:value="tinted" … </activity-alias>
 * ```
 *
 * Il resto lo fa `engine-account`, **senza codice nell'app**:
 * - quando lo Store avvisa del cambio, un'app che **non** è in primo piano cambia subito l'alias acceso: così le app
 *   chiuse cambiano tutte insieme;
 * - un'app aperta lo cambia **appena passa in sottofondo** (`onTrimMemory(TRIM_MEMORY_UI_HIDDEN)`), mai mentre la si
 *   usa: alcuni launcher chiudono o tolgono ciò che punta all'alias spento.
 *
 * Prima si accende il nuovo, poi si spegne il vecchio, sempre con `DONT_KILL_APP`: non c'è un istante senza icona.
 * Senza Store l'icona resta com'è. Una famiglia che l'app non ha vale l'icona di serie.
 */
class PampaAppIcons internal constructor(private val host: Host) {

  /** Le domande al sistema, sostituibili nelle prove. */
  interface Host {
    fun aliases(): List<PampaIconAlias>
    fun isEnabled(alias: PampaIconAlias): Boolean
    /** Accende o spegne [alias] tornando, se si può, allo stato del manifest (così l'icona di serie non lascia tracce). */
    fun setEnabled(alias: PampaIconAlias, enabled: Boolean)
    /** L'app ha qualcosa di visibile adesso? */
    fun inForeground(): Boolean
    /** Chiama [callback] ogni volta che l'app passa in sottofondo. Si registra una volta sola. */
    fun listenBackground(callback: () -> Unit)
  }

  private var aliasesCache: List<PampaIconAlias>? = null
  private var listening = false

  /** La famiglia che aspetta il sottofondo, o `null`. */
  @Volatile var pending: String? = null
    private set

  private fun aliases(): List<PampaIconAlias> = aliasesCache ?: runCatching { host.aliases() }.getOrDefault(emptyList()).also { aliasesCache = it }

  /** L'app ha alias d'icona? Se no, `engine-account` non guarda nemmeno l'aspetto per l'icona. */
  val hasAliases: Boolean get() = synchronized(this) { aliases().isNotEmpty() }

  /** La famiglia dell'icona accesa adesso, o `null` se l'app non ha alias. */
  fun currentIconSet(): String? = synchronized(this) { aliases().firstOrNull(host::isEnabled)?.iconSet }

  /** Chiede la famiglia [iconSet] (`null`: quella di serie). Adesso, o al primo sottofondo se l'app è aperta. */
  fun request(iconSet: String?): PampaIconOutcome = synchronized(this) {
    val aliases = aliases()
    if (aliases.isEmpty()) return PampaIconOutcome.NO_ALIASES
    val target = targetFor(aliases, iconSet)
    if (isOnly(aliases, target)) {
      pending = null
      return PampaIconOutcome.UNCHANGED
    }
    if (host.inForeground()) {
      pending = target.iconSet
      if (!listening) {
        listening = true
        host.listenBackground(::onBackground)
      }
      return PampaIconOutcome.DEFERRED
    }
    switchTo(aliases, target)
    pending = null
    PampaIconOutcome.APPLIED
  }

  private fun onBackground() {
    synchronized(this) {
      val set = pending ?: return
      val aliases = aliases()
      val target = targetFor(aliases, set)
      if (!isOnly(aliases, target)) switchTo(aliases, target)
      pending = null
    }
  }

  private fun targetFor(aliases: List<PampaIconAlias>, iconSet: String?): PampaIconAlias =
    aliases.firstOrNull { it.iconSet == iconSet } ?: aliases.firstOrNull { it.enabledInManifest } ?: aliases.first()

  private fun isOnly(aliases: List<PampaIconAlias>, target: PampaIconAlias): Boolean =
    aliases.all { host.isEnabled(it) == (it == target) }

  private fun switchTo(aliases: List<PampaIconAlias>, target: PampaIconAlias) {
    // Prima il nuovo, poi il vecchio: il launcher non vede mai l'app senza icona.
    host.setEnabled(target, true)
    for (a in aliases) if (a != target && host.isEnabled(a)) host.setEnabled(a, false)
  }

  companion object {
    @Volatile private var instance: PampaAppIcons? = null

    /** Le icone di questo processo. */
    fun of(context: Context): PampaAppIcons = instance ?: synchronized(this) {
      instance ?: PampaAppIcons(SystemIconHost(context.applicationContext)).also { instance = it }
    }

    /** Per le prove: un'istanza con un [Host] finto, o `null` per tornare a quella vera. */
    internal fun install(icons: PampaAppIcons?) {
      instance = icons
    }
  }
}

/** Le risposte vere del sistema: `PackageManager` per gli alias, l'importanza del processo per il primo piano. */
internal class SystemIconHost(private val context: Context) : PampaAppIcons.Host {
  private val pm = context.packageManager

  @Suppress("DEPRECATION")
  override fun aliases(): List<PampaIconAlias> {
    val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
    val info = if (Build.VERSION.SDK_INT >= 33) {
      pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
      pm.getPackageInfo(context.packageName, flags)
    }
    return info.activities.orEmpty().mapNotNull { a ->
      val set = a.metaData?.getString(AccountProtocol.META_ICON_SET) ?: return@mapNotNull null
      if (a.targetActivity == null) return@mapNotNull null
      PampaIconAlias(a.name, set, a.enabled)
    }
  }

  private fun component(alias: PampaIconAlias) = ComponentName(context.packageName, alias.className)

  override fun isEnabled(alias: PampaIconAlias): Boolean = when (pm.getComponentEnabledSetting(component(alias))) {
    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> alias.enabledInManifest
    else -> false
  }

  override fun setEnabled(alias: PampaIconAlias, enabled: Boolean) {
    val state = when {
      enabled == alias.enabledInManifest -> PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
      enabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
    pm.setComponentEnabledSetting(component(alias), state, PackageManager.DONT_KILL_APP)
  }

  override fun inForeground(): Boolean {
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
  }

  override fun listenBackground(callback: () -> Unit) {
    context.applicationContext.registerComponentCallbacks(object : ComponentCallbacks2 {
      override fun onTrimMemory(level: Int) {
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) callback()
      }

      override fun onConfigurationChanged(newConfig: Configuration) = Unit

      @Deprecated("Deprecated in Java")
      override fun onLowMemory() = Unit
    })
  }
}
