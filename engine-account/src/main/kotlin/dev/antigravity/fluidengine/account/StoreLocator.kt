package dev.antigravity.fluidengine.account

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * Come un'app trova Pampa Store (`b\api.md` § 5.1; architettura § 2.2). Lo Store di sviluppo esiste solo per chi
 * costruisce le app: [devBuild] di serie segue il flag `debuggable` dell'app ospite.
 */
data class AccountClientConfig(
  /** Lo Store vero. */
  val storePackage: String = "com.pampa.store",
  /** Lo Store di sviluppo, con la sua autorità e la stessa firma `pampa.jks`. */
  val devStorePackage: String = "com.pampa.store.debug",
  /** Una build di sviluppo dell'app? Se `null`, lo dice il flag `debuggable` dell'app. */
  val devBuild: Boolean? = null,
)

/** Lo Store trovato: pacchetto, autorità e versione. */
data class StoreHost(val packageName: String, val authority: String, val versionName: String?)

/**
 * Trova lo Store e decide se fidarsi (critica M1 e M9):
 *
 * 1. **La precedenza** ([candidates]). Le build vere dell'app cercano solo lo Store vero e ignorano quello di sviluppo;
 *    le build di sviluppo cercano prima lo Store di sviluppo, poi quello vero. Così un'app non finisce con due account.
 * 2. **Il riconoscimento**: il pacchetto ha il provider `<pacchetto>.account` col meta-data `dev.pampa.account.HOST = 1`.
 * 3. **Il controllo inverso**: lo Store deve avere **la stessa firma** dell'app. Un `com.pampa.store` firmato da altri
 *    per l'app è «Store assente» (`NoStore`), e non riceve nemmeno una domanda.
 *
 * Le tre domande al sistema stanno in [Probe], così le prove le sostituiscono: il `PackageManager` di Robolectric
 * risponde sempre «firme uguali».
 */
class StoreLocator(private val config: AccountClientConfig, private val devBuild: Boolean, private val probe: Probe) {

  /** Le domande al sistema. */
  interface Probe {
    /** Il provider di [authority] se appartiene a [packageName] ed è installato, coi suoi meta-data; altrimenti `null`. */
    fun provider(packageName: String, authority: String): Bundle?
    /** Lo Store ha la stessa firma di quest'app? */
    fun sameSignature(packageName: String): Boolean
    fun versionName(packageName: String): String?
  }

  /** I pacchetti dello Store da provare, nell'ordine (M9). */
  fun candidates(): List<String> = if (devBuild) listOf(config.devStorePackage, config.storePackage) else listOf(config.storePackage)

  /** Lo Store di cui fidarsi, o `null`. */
  fun locate(): StoreHost? {
    for (pkg in candidates()) {
      val authority = pkg + AccountProtocol.AUTHORITY_SUFFIX
      val meta = probe.provider(pkg, authority) ?: continue
      if (meta.getInt(AccountProtocol.META_HOST, 0) != 1) continue
      if (!probe.sameSignature(pkg)) continue
      return StoreHost(pkg, authority, probe.versionName(pkg))
    }
    return null
  }

  companion object {
    /** Le domande vere al `PackageManager`. */
    fun systemProbe(context: Context): Probe = object : Probe {
      private val pm = context.packageManager

      override fun provider(packageName: String, authority: String): Bundle? {
        val info = runCatching { pm.resolveContentProvider(authority, PackageManager.GET_META_DATA) }.getOrNull() ?: return null
        if (info.packageName != packageName) return null
        return info.metaData ?: Bundle()
      }

      override fun sameSignature(packageName: String): Boolean =
        runCatching { pm.checkSignatures(context.packageName, packageName) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)

      override fun versionName(packageName: String): String? = runCatching { pm.getPackageInfo(packageName, 0).versionName }.getOrNull()
    }

    /** La build dell'app ospite è di sviluppo? Il flag `debuggable` del manifest, che le build vere non hanno. */
    fun isDebuggable(context: Context): Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
  }
}
