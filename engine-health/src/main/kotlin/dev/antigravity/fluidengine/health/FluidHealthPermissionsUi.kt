/*
 * engine-health (4.0, F8.26): i permessi misura per misura (le righe e il foglio di Health Connect), la pagina della
 * privacy e le licenze. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.health

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.theme.FluidInlineMessage
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.FluidTone
import dev.antigravity.fluidengine.ui.theme.FluidToneTile

/**
 * Lo stato di una misura per le righe dei permessi: cosa l'app chiede ([asked]) e cosa l'utente ha dato ([granted]).
 */
enum class FluidHealthAccess { Allowed, ReadOnly, WriteOnly, NotAllowed }

/** Lo stato di [metric] dati i permessi [asked] (dell'app) e [granted] (dell'utente). */
fun fluidHealthAccess(metric: FluidHealthMetric, asked: Collection<FluidHealthPermission>, granted: Set<FluidHealthPermission>): FluidHealthAccess {
  val wantsRead = FluidHealthPermission.read(metric) in asked
  val wantsWrite = FluidHealthPermission.write(metric) in asked
  val read = FluidHealthPermission.read(metric) in granted
  val write = FluidHealthPermission.write(metric) in granted
  return when {
    (!wantsRead || read) && (!wantsWrite || write) && (read || write) -> FluidHealthAccess.Allowed
    read && wantsWrite && !write -> FluidHealthAccess.ReadOnly
    write && wantsRead && !read -> FluidHealthAccess.WriteOnly
    else -> FluidHealthAccess.NotAllowed
  }
}

/**
 * Chi chiede i permessi: il foglio di Health Connect, misura per misura. Dopo **due rifiuti** della stessa misura Android
 * non mostra piu' il foglio (risponde subito con niente): allora il tasto della riga apre la pagina dell'app in Health
 * Connect. I rifiuti si contano qui, per la vita della pagina.
 */
@Stable
class FluidHealthPermissionRequester internal constructor(
  private val launch: (Set<String>) -> Unit,
  private val openHealthConnect: () -> Unit,
) {
  internal val denials = mutableStateMapOf<FluidHealthMetric, Int>()
  internal var pending: Set<FluidHealthPermission> = emptySet()

  /** Vero se per [metric] il foglio non si mostra piu' (due rifiuti): serve Health Connect. */
  fun needsSettings(metric: FluidHealthMetric): Boolean = (denials[metric] ?: 0) >= 2

  /** Chiede [permissions] (di una misura sola, di solito) col foglio, o apre Health Connect dopo due rifiuti. */
  fun request(permissions: Set<FluidHealthPermission>) {
    if (permissions.isEmpty()) return
    if (permissions.any { needsSettings(it.metric) }) {
      openHealthConnect()
      return
    }
    pending = permissions
    launch(FluidHealthPermissions.strings(permissions))
  }

  internal fun onResult(granted: Set<String>) {
    val given = FluidHealthPermissions.fromStrings(granted)
    for (metric in pending.map { it.metric }.distinct()) {
      val wanted = pending.filter { it.metric == metric }
      if (wanted.none { it in given }) denials[metric] = (denials[metric] ?: 0) + 1
    }
    pending = emptySet()
  }
}

/**
 * Il richiedente dei permessi della pagina: [onResult] riceve i permessi del modulo dati dall'utente (anche nessuno).
 */
@Composable
fun rememberFluidHealthPermissionRequester(onResult: (Set<FluidHealthPermission>) -> Unit): FluidHealthPermissionRequester {
  val context = LocalContext.current
  var requester by remember { mutableStateOf<FluidHealthPermissionRequester?>(null) }
  val launcher = rememberLauncherForActivityResult(FluidHealthPermissions.requestContract()) { granted ->
    requester?.onResult(granted)
    onResult(FluidHealthPermissions.fromStrings(granted))
  }
  return remember(launcher) {
    FluidHealthPermissionRequester(
      launch = { launcher.launch(it) },
      openHealthConnect = { openHealthConnect(context) },
    ).also { requester = it }
  }
}

/** Apre la pagina dei permessi di questa app in Health Connect. Falso se non si puo' (Health Connect assente). */
fun openHealthConnect(context: Context): Boolean = try {
  context.startActivity(FluidHealthPermissions.manageIntent(context).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
  true
} catch (_: ActivityNotFoundException) {
  false
} catch (_: SecurityException) {
  false
}

/**
 * Le righe dei permessi, una per misura, come la pagina dei permessi di Salute: il simbolo nel colore della misura, il
 * nome, cosa l'app chiede («Lettura e scrittura»), e lo stato («Consentito», «Solo lettura», «Non consentito»). Toccata,
 * una riga chiede i permessi che mancano **di quella misura sola** ([onRequest]). Se Health Connect non c'e' o va
 * aggiornato, sopra c'e' l'avviso con il tasto.
 */
@Composable
fun FluidHealthPermissionRows(
  asked: List<FluidHealthPermission>,
  granted: Set<FluidHealthPermission>,
  onRequest: (Set<FluidHealthPermission>) -> Unit,
  modifier: Modifier = Modifier,
  availability: FluidHealthAvailability = FluidHealthAvailability.Available,
  onOpenHealthConnect: (() -> Unit)? = null,
  needsSettings: (FluidHealthMetric) -> Boolean = { false },
  labels: FluidHealthLabels = FluidHealthLabels(),
  glass: Boolean = false,
) {
  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val metrics = asked.map { it.metric }.distinct()
  Column(modifier) {
    if (availability != FluidHealthAvailability.Available) {
      FluidInlineMessage(
        message = if (availability == FluidHealthAvailability.NeedsUpdate) labels.needsUpdate else labels.unavailable,
        tone = FluidTone.Warning,
        action = if (availability == FluidHealthAvailability.NeedsUpdate && onOpenHealthConnect != null) labels.openHealthConnect else null,
        onClick = if (availability == FluidHealthAvailability.NeedsUpdate) onOpenHealthConnect else null,
        modifier = Modifier.padding(bottom = 12.dp),
      )
    }
    FluidListGroup(glass = glass) {
      metrics.forEachIndexed { i, metric ->
        if (i > 0) FluidListDivider()
        val wantsRead = FluidHealthPermission.read(metric) in asked
        val wantsWrite = FluidHealthPermission.write(metric) in asked
        val what = when {
          wantsRead && wantsWrite -> "${labels.read} e ${labels.write.lowercase()}"
          wantsWrite -> labels.write
          else -> labels.read
        }
        val access = fluidHealthAccess(metric, asked, granted)
        val state = when (access) {
          FluidHealthAccess.Allowed -> labels.allowed
          FluidHealthAccess.ReadOnly -> labels.readOnly
          FluidHealthAccess.WriteOnly -> labels.writeOnly
          FluidHealthAccess.NotAllowed -> if (needsSettings(metric)) labels.askAgain else labels.notAllowed
        }
        val missing = asked.filter { it.metric == metric && it !in granted }.toSet()
        FluidListRow(
          title = labels.metricName(metric),
          subtitle = what,
          meta = state,
          tone = if (access == FluidHealthAccess.Allowed) FluidTone.Success else FluidTone.Neutral,
          leading = {
            FluidToneTile(color = FluidHealthColors.of(metric, dark)) {
              FluidIcon(FluidHealthColors.icon(metric), contentDescription = null, tint = FluidHealthColors.of(metric, dark), size = 18.dp)
            }
          },
          onClick = if (missing.isNotEmpty() && availability == FluidHealthAvailability.Available) ({ onRequest(missing) }) else null,
          disclosure = missing.isNotEmpty(),
          modifier = Modifier.testTag(FluidHealthTags.permission(metric)),
        )
      }
    }
  }
}

/**
 * La pagina della privacy che Health Connect apre dal suo elenco dei permessi (il manifest del modulo la dichiara): dice
 * in parole semplici che cosa l'app chiede, misura per misura (dai permessi scritti nel suo manifest), che i dati restano
 * sul telefono cifrati e fuori da ogni statistica, e porta all'informativa dell'app (il meta-data
 * `dev.antigravity.fluidengine.health.PRIVACY_URL` nel manifest dell'app).
 */
class FluidHealthPrivacyActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val declared = FluidHealthPermissions.declared(this)
    val label = runCatching { packageManager.getApplicationLabel(applicationInfo).toString() }.getOrDefault("")
    val url = runCatching {
      packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA).metaData?.getString(PrivacyUrlKey)
    }.getOrNull()
    setContent {
      FluidTheme(EngineSettings()) {
        FluidHealthPrivacyPage(appName = label, asked = declared.toList(), privacyUrl = url, onBack = { finish() })
      }
    }
  }

  companion object {
    /** Il meta-data dell'app con l'indirizzo della sua informativa sulla privacy. */
    const val PrivacyUrlKey: String = "dev.antigravity.fluidengine.health.PRIVACY_URL"
  }
}

/** La pagina della privacy dei dati di salute (anche da mettere nelle impostazioni dell'app). */
@Composable
fun FluidHealthPrivacyPage(
  appName: String,
  asked: List<FluidHealthPermission>,
  privacyUrl: String?,
  onBack: () -> Unit,
  labels: FluidHealthLabels = FluidHealthLabels(),
) {
  val uri = LocalUriHandler.current
  FluidScreen(title = "Salute e privacy", onBack = onBack) {
    item(key = "testo") {
      Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
          "$appName legge da Health Connect solo le misure qui sotto, e scrive solo quelle segnate. I dati restano sul " +
            "telefono, cifrati, e non finiscono in nessuna statistica, rapporto di errori o servizio esterno.",
          style = MaterialTheme.typography.bodyLarge,
        )
      }
    }
    item(key = "misure") {
      FluidHealthPermissionRows(asked = asked, granted = asked.toSet(), onRequest = {}, labels = labels, modifier = Modifier.padding(16.dp))
    }
    if (privacyUrl != null) {
      item(key = "informativa") {
        FluidButton(
          text = "Leggi l'informativa",
          onClick = { runCatching { uri.openUri(privacyUrl) } },
          style = FluidButtonStyle.Tinted,
          modifier = Modifier.padding(16.dp),
        )
      }
    }
  }
}

/**
 * Le licenze di `engine-health`, da aggiungere alla pagina «Licenze e crediti».
 *
 * ```kotlin
 * fluidLicensesSection(licences = FluidEngineLicenses + FluidHealthLicenses)
 * ```
 */
val FluidHealthLicenses: List<FluidLicense> = listOf(
  FluidLicense("AndroidX Health Connect", "Android Open Source Project", "Apache-2.0",
    "https://developer.android.com/jetpack/androidx/releases/health-connect", "I dati di salute e movimento"),
  FluidLicense("Guava", "Google", "Apache-2.0", "https://github.com/google/guava", "Portata da Health Connect"),
)
