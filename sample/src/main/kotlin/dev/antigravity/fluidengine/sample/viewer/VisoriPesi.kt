package dev.antigravity.fluidengine.sample.viewer

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSectionFootnote
import dev.antigravity.fluidengine.ui.fluid.FluidSectionHeader
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.viewer.archive.FluidArchiveLicenses
import dev.antigravity.fluidengine.viewer.graphics.FluidGraphicsViewerLicenses
import dev.antigravity.fluidengine.viewer.office.FluidOfficeLicenses
import dev.antigravity.fluidengine.viewer.scene.FluidSceneViewerLicenses
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

// F6C.15 · La pagina «Pesi»: quanto costa ogni modulo dei visori, per architettura, con le licenze.
//
// I numeri non sono scritti qui: la pagina legge `engine-viewer/families.json`, la lista ammessa che leggono anche
// `checkViewerFamilies` e `tools/engine-install.ps1`. Il build della galleria la copia negli asset
// (`copyVisoriFamilies`, sample/build.gradle), così pagina, controllo di build e installatore dicono la stessa cosa.

/** Le architetture di un APK Android, nell'ordine della pagina. */
internal val VisoriAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")

/** Una libreria o un file che un modulo porta nell'APK, con la licenza e il peso dichiarati. */
@Immutable
internal data class VisoriPart(val name: String, val license: String, val weight: String?)

/**
 * Un modulo di famiglia letto da `families.json`. [abiBytes] è il peso misurato per architettura (la voce `apk`, F6C.15),
 * vuoto se il modulo dichiara solo la frase [weight]; [measuredBy] dice come e da chi è stato misurato.
 */
@Immutable
internal data class VisoriModuleWeight(
  val module: String,
  val family: String,
  val label: String,
  val weight: String,
  val abiBytes: Map<String, Long>,
  val measuredBy: String?,
  val dexBytes: Long?,
  val assetBytes: Long?,
  val nativeBytes: Map<String, Long>,
  val libraries: List<VisoriPart>,
  val assets: List<VisoriPart>,
  val requires: List<String>,
)

internal object VisoriWeights {
  /** Dove il build della galleria mette la copia di `engine-viewer/families.json`. */
  const val Asset = "visori/families.json"

  /** Le famiglie ammesse, senza il modulo di prova di F6C.8 (`"test": true`). */
  fun parse(json: String): List<VisoriModuleWeight> {
    val families = JSONObject(json).getJSONArray("families")
    return (0 until families.length()).map(families::getJSONObject).filter { !it.optBoolean("test", false) }.map { f ->
      val apk = f.optJSONObject("apk")
      fun parts(key: String, name: String) = f.optJSONArray(key)?.let { array ->
        (0 until array.length()).map(array::getJSONObject).map {
          VisoriPart(it.getString(name), it.optString("license"), it.optString("weight").ifBlank { null })
        }
      }.orEmpty()
      fun bytes(obj: JSONObject?) = obj?.let { o -> VisoriAbis.filter(o::has).associateWith(o::getLong) }.orEmpty()
      VisoriModuleWeight(
        module = f.getString("module"),
        family = f.getString("family"),
        label = f.optString("label"),
        weight = f.optString("weight"),
        abiBytes = bytes(apk),
        measuredBy = apk?.optString("source")?.ifBlank { null },
        dexBytes = apk?.takeIf { it.has("dex") }?.getLong("dex"),
        assetBytes = apk?.takeIf { it.has("assets") }?.getLong("assets"),
        nativeBytes = bytes(apk?.optJSONObject("native")),
        libraries = parts("libraries", "coordinates"),
        assets = parts("assets", "name"),
        requires = f.optJSONArray("requires")?.let { r -> (0 until r.length()).map(r::getString) }.orEmpty(),
      )
    }
  }

  suspend fun read(context: Context): List<VisoriModuleWeight> = withContext(Dispatchers.IO) {
    parse(context.assets.open(Asset).bufferedReader(Charsets.UTF_8).use { it.readText() })
  }
}

/**
 * Le licenze delle librerie e dei caratteri che i moduli dei visori portano nella galleria, da aggiungere a
 * `FluidEngineLicenses` nella sezione delle licenze. engine-viewer-data non porta librerie; engine-viewer-ar non è
 * nella galleria.
 */
internal val VisoriLicenses: List<FluidLicense>
  get() = (FluidOfficeLicenses + FluidArchiveLicenses + FluidGraphicsViewerLicenses + FluidSceneViewerLicenses)
    .distinctBy { it.name to it.licence }

/** I moduli che la galleria include: tutti quelli di `engine-viewer-all` tranne l'AR, che chiede la fotocamera. */
internal val VisoriGalleryModules: Set<String> = VisoriFamiglia.entries.map { it.module }.toSet()

/** Una riga della pagina «Pesi». */
internal data class VisoriWeightRow(val title: String, val subtitle: String, val meta: String? = null)

/** Le righe di un modulo: il peso per architettura (o la frase del modulo), le librerie e gli asset, se è nella galleria. */
internal fun weightRows(module: VisoriModuleWeight): List<VisoriWeightRow> = buildList {
  if (module.abiBytes.isNotEmpty()) {
    for ((abi, bytes) in module.abiBytes) {
      val native = module.nativeBytes[abi]?.takeIf { it > 0 }
      add(VisoriWeightRow(abi, native?.let { "Codice e asset, più ${formatBytes(it)} di librerie native" }
        ?: "Codice e asset, nessuna libreria nativa", formatBytes(bytes)))
    }
    module.dexBytes?.let { add(VisoriWeightRow("Codice (DEX)", "Uguale per ogni architettura", formatBytes(it))) }
    module.assetBytes?.takeIf { it > 0 }?.let {
      add(VisoriWeightRow("Asset", "Caratteri, materiali e ambienti, uguali per ogni architettura", formatBytes(it)))
    }
  } else {
    add(VisoriWeightRow("Peso dichiarato", module.weight))
  }
  for (part in module.libraries) add(VisoriWeightRow(part.name, part.license.ifBlank { "Licenza non dichiarata" }))
  for (part in module.assets) {
    add(VisoriWeightRow(part.name, listOfNotNull(part.license.ifBlank { null }, part.weight).joinToString(" · ")
      .ifBlank { "Licenza non dichiarata" }))
  }
  val presence = when {
    module.module in VisoriGalleryModules -> "Sì: la galleria lo include con engine-viewer-all."
    module.family == "ar" -> "No: chiede la fotocamera, e la galleria non la chiede (checkArViewerManifests)."
    else -> "No."
  }
  add(VisoriWeightRow("In Fluid Glass", presence +
    module.requires.takeIf { it.isNotEmpty() }?.let { " Porta con sé ${it.joinToString()}." }.orEmpty()))
}

@Composable
internal fun VisoriPesiPage(onBack: () -> Unit) {
  val context = LocalContext.current
  val weights by produceState<List<VisoriModuleWeight>?>(null, context) { value = VisoriWeights.read(context) }
  FluidScreen(title = "Pesi", subtitle = "Quanto costa ogni modulo", onBack = onBack) {
    item {
      Text("Ogni famiglia è un modulo a parte: un'app che non lo aggiunge non ne paga il peso, e il tipo resta " +
        "sconosciuto con «Apri con…». I numeri sono quelli di engine-viewer/families.json, la stessa lista che " +
        "controlla il build e che legge engine-install.ps1.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    for (module in weights.orEmpty()) {
      item(key = "pesi-${module.module}") {
        FluidSectionHeader(module.module, detail = module.label)
      }
      item(key = "pesi-${module.module}-righe") {
        val rows = remember(module) { weightRows(module) }
        FluidListGroup(Modifier.testTag("pesi-${module.module}")) {
          rows.forEachIndexed { index, row ->
            if (index > 0) FluidListDivider()
            FluidListRow(row.title, subtitle = row.subtitle, meta = row.meta, disclosure = false)
          }
        }
      }
      item(key = "pesi-${module.module}-fonte") {
        FluidSectionFootnote(module.measuredBy?.let { "Misura: $it. Nelle parole del modulo: ${module.weight}" }
          ?: "Misura: ${module.weight}")
      }
    }
    fluidLicensesSection(title = "Licenze dei moduli", footnote = "Le librerie e i caratteri che i moduli dei " +
      "visori portano nella galleria. Si aggiungono a quelle dell'engine nella pagina Impostazioni.",
      licences = VisoriLicenses)
  }
}
