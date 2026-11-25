package dev.antigravity.fluidengine.ui.fluid.share

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Locale

/** Un'app della fila in cima al foglio: l'attivita' che riceve la condivisione, col nome e l'icona di Android. */
@Immutable
class FluidShareTarget(
  val packageName: String,
  val activityName: String,
  val label: String,
  val icon: ImageBitmap?,
) {
  override fun equals(other: Any?): Boolean =
    other is FluidShareTarget && other.packageName == packageName && other.activityName == activityName

  override fun hashCode(): Int = packageName.hashCode() * 31 + activityName.hashCode()
  override fun toString(): String = "FluidShareTarget($packageName/$activityName, $label)"
}

/**
 * Di quali app fidarsi per la fila del foglio: la stessa verifica di `engine-ai-bridge`, la firma.
 *
 * - [SameSigner] (di serie): le app firmate con la stessa chiave di questa, come fa `AiToolHostProvider` con
 *   `checkSignatures`. E' la famiglia Pampa per un'app firmata con la chiave di Pampa.
 * - [certificates]: le app il cui certificato di firma ha una di queste impronte SHA-256 (esadecimale, con o senza i
 *   due punti). Serve all'app che sa l'impronta dal manifest dello store e non e' firmata con quella chiave (una build di
 *   prova), o a chi vuole fidarsi di piu' chiavi.
 *
 * In tutti e due i casi il nome del pacchetto da solo non basta: un'app qualunque puo' chiamarsi come una di Pampa.
 */
@Immutable
class FluidShareTrust private constructor(internal val sameSigner: Boolean, internal val sha256: Set<String>) {
  companion object {
    /** Le app firmate con la stessa chiave di questa. */
    val SameSigner: FluidShareTrust = FluidShareTrust(true, emptySet())

    /** Le app con un certificato di firma fra queste impronte SHA-256. */
    fun certificates(sha256: Set<String>): FluidShareTrust =
      FluidShareTrust(false, sha256.map(::fluidNormalizeDigest).filter { it.length == 64 }.toSet())
  }
}

/**
 * La fila delle app Pampa (4.0, F8.10). Android non lascia elencare tutte le app (servirebbe `QUERY_ALL_PACKAGES`, che
 * l'engine non chiede): `engine-ui` dichiara nel manifest un `<queries>` per le sole app che ricevono `SEND`, e fra
 * quelle il foglio tiene le app fidate ([FluidShareTrust]). Le altre restano al foglio di Android («Altre app…»).
 *
 * L'ordine: prima le app scelte di recente da questo foglio (le ultime in cima), poi per nome.
 */
object FluidShareTargets {
  /** Quante app al massimo nella fila. */
  const val MaxTargets: Int = 8

  /** Le app fidate che ricevono [item], senza questa. Legge il `PackageManager`: chiamare fuori dal thread principale. */
  fun find(
    context: Context,
    item: FluidShareItem,
    trust: FluidShareTrust = FluidShareTrust.SameSigner,
    limit: Int = MaxTargets,
  ): List<FluidShareTarget> {
    val pm = context.packageManager
    val own = context.packageName
    val probe = Intent(FluidShareIntents.send(item)).setComponent(null)
    @Suppress("DEPRECATION")
    val activities = runCatching { pm.queryIntentActivities(probe, PackageManager.MATCH_DEFAULT_ONLY) }.getOrDefault(emptyList())
    val recent = FluidShareRecents.read(context)
    val iconPx = (context.resources.displayMetrics.density * 60).toInt().coerceAtLeast(1)
    return activities.asSequence()
      .filter { it.activityInfo.packageName != own }
      .filter { fluidTrusts(pm, own, it.activityInfo.packageName, trust) }
      .distinctBy { it.activityInfo.packageName to it.activityInfo.name }
      .map { info ->
        val label = info.loadLabel(pm).toString()
        val icon = runCatching { info.loadIcon(pm).toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull()
        FluidShareTarget(info.activityInfo.packageName, info.activityInfo.name, label, icon)
      }
      .sortedWith(fluidTargetOrder(recent))
      .take(limit)
      .toList()
  }

  /** Ricorda che [target] e' stato scelto: la prossima volta sta in cima. */
  fun remember(context: Context, target: FluidShareTarget) = FluidShareRecents.add(context, target.packageName)
}

/** Le app della fila per [item], lette fuori dal thread principale: null finche' la lettura non finisce. */
@Composable
fun rememberFluidShareTargets(item: FluidShareItem?, trust: FluidShareTrust = FluidShareTrust.SameSigner): State<List<FluidShareTarget>?> {
  val context = LocalContext.current
  return produceState<List<FluidShareTarget>?>(initialValue = null, item?.mimeType, item?.uris?.size, trust) {
    value = if (item == null) emptyList() else withContext(Dispatchers.IO) { FluidShareTargets.find(context, item, trust) }
  }
}

/** Prima i recenti (l'ultimo in cima), poi per nome nella lingua del dispositivo. */
internal fun fluidTargetOrder(recent: List<String>): Comparator<FluidShareTarget> =
  compareBy<FluidShareTarget> { recent.indexOf(it.packageName).let { i -> if (i < 0) Int.MAX_VALUE else i } }
    .thenBy { it.label.lowercase(Locale.getDefault()) }

/** La verifica della firma di [pkg] secondo [trust]. */
internal fun fluidTrusts(pm: PackageManager, own: String, pkg: String, trust: FluidShareTrust): Boolean {
  if (trust.sameSigner) return runCatching { pm.checkSignatures(own, pkg) == PackageManager.SIGNATURE_MATCH }.getOrDefault(false)
  if (trust.sha256.isEmpty()) return false
  return fluidSigningDigests(pm, pkg).any { it in trust.sha256 }
}

/** Le impronte SHA-256 dei certificati di firma di [pkg] (la firma corrente, anche dopo una rotazione della chiave). */
internal fun fluidSigningDigests(pm: PackageManager, pkg: String): Set<String> = runCatching {
  val signatures = if (Build.VERSION.SDK_INT >= 28) {
    val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return@runCatching emptySet()
    if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
  } else {
    @Suppress("DEPRECATION")
    pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures
  }
  signatures.orEmpty().map { sig -> fluidSha256Hex(sig.toByteArray()) }.toSet()
}.getOrDefault(emptySet())

internal fun fluidSha256Hex(bytes: ByteArray): String =
  MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

internal fun fluidNormalizeDigest(digest: String): String = digest.filter { it.isLetterOrDigit() }.lowercase(Locale.ROOT)

/** Le ultime app scelte dal foglio, nelle preferenze dell'app (solo nomi di pacchetto, al massimo [Max]). */
internal object FluidShareRecents {
  private const val Prefs = "fluid-share-recents"
  private const val Key = "packages"
  private const val Max = 8

  fun read(context: Context): List<String> =
    context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).getString(Key, null)
      ?.split('\n')?.filter { it.isNotBlank() }.orEmpty()

  fun add(context: Context, pkg: String) {
    val next = (listOf(pkg) + read(context).filter { it != pkg }).take(Max)
    context.getSharedPreferences(Prefs, Context.MODE_PRIVATE).edit().putString(Key, next.joinToString("\n")).apply()
  }
}
