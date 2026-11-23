package dev.antigravity.fluidengine.viewer.data

import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.icons.MoreHoriz
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoAction
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerFile
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import java.io.File

/**
 * F6C.13 · L'app di prova delle pagine della famiglia «data», solo nell'APK delle prove (src/androidTest): i file di
 * prova copiati dagli asset, il visore vero di engine-ui col registro di engine-viewer, il pannello info da
 * [fluidDataViewerInfo]. Non è la galleria (che è di F6C.15).
 *
 * Extra: `file` (il nome del file da cui partire), `dark` (tema scuro), `font` (la scala del testo, 2,0 per le riprese
 * grandi; di serie quella di sistema).
 */
class DataProvaActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    val dir = prepare(this.filesDir, this)
    val dark = intent.getBooleanExtra("dark", false)
    val start = intent.getStringExtra("file")
    // La scala del testo della prova (1,0 o 2,0) senza toccare le impostazioni del dispositivo.
    val font = intent.getFloatExtra("font", 0f)
    // La famiglia con la cartella dei file di prova: le risorse relative dell'HTML si leggono da lì, e solo da lì.
    FluidViewerRegistry.install(FluidDataFamily(FluidDataSettings(resources = { _, path -> DataProva.resource(dir, path) })))
    val names = DataProva.Files
    val files = names.map { name -> File(dir, name).let { FluidViewerFile(Uri.fromFile(it), name, null, it.length()) } }
    setContent {
      val system = LocalDensity.current
      CompositionLocalProvider(LocalDensity provides if (font > 0f) Density(system.density, font) else system) {
      FluidTheme(settings = remember { EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColorEnabled = false) }) {
        val opened = rememberFluidViewerFiles(files, openWith = {}, share = {})
        FluidViewerInfoProvider({ i ->
          opened.document(i)?.let {
            fluidDataViewerInfo(it, actions = listOf(FluidViewerInfoAction("Apri con…") {}, FluidViewerInfoAction("Condividi") {}))
          }
        }) {
          FluidFileViewer(
            count = files.size,
            initial = names.indexOf(start).coerceAtLeast(0),
            content = opened::content,
            onBack = { finish() },
            toolbar = {
              button("altro", FluidIcons.MoreHoriz, "Altro", {}, placement = FluidToolbarPlacement.BottomBar)
              button("condividi", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar)
            },
          )
        }
      }
      }
    }
  }

  companion object {
    /** Copia gli asset `data/` nei file dell'app (una volta per avvio: i file di prova sono piccoli). */
    fun prepare(filesDir: File, activity: ComponentActivity): File {
      val dir = File(filesDir, "data")
      fun copy(path: String) {
        val children = activity.assets.list(path).orEmpty()
        if (children.isEmpty()) {
          val out = File(filesDir, path)
          out.parentFile?.mkdirs()
          activity.assets.open(path).use { input -> out.outputStream().use { input.copyTo(it) } }
        } else {
          children.forEach { copy("$path/$it") }
        }
      }
      copy("data")
      // I due file grandi si scrivono qui (una volta): 100.000 righe di CSV e un JSON di 20.000 oggetti.
      val csv = File(dir, "grande.csv")
      if (!csv.exists()) csv.bufferedWriter().use { w ->
        w.write("id;città;importo;data;nota\n")
        for (i in 0 until 100_000) w.write("$i;${DataProva.Cities[i % DataProva.Cities.size]};${i % 997},${i % 100};2026-11-${1 + i % 28};\"riga $i, con ; dentro\"\n")
      }
      val json = File(dir, "grande.json")
      if (!json.exists()) json.bufferedWriter().use { w ->
        w.write("{\"titolo\": \"Misure\", \"righe\": [\n")
        for (i in 0 until 20_000) {
          if (i > 0) w.write(",\n")
          w.write("  {\"id\": $i, \"città\": \"${DataProva.Cities[i % DataProva.Cities.size]}\", \"valore\": ${i * 0.5}, \"ok\": ${i % 3 != 0}, \"nota\": null}")
        }
        w.write("\n]}\n")
      }
      return dir
    }
  }
}

/** I file di prova, in ordine, e la cartella concessa alle risorse relative. */
object DataProva {
  val Files = listOf(
    "nota.md", "pagina.html", "spese.csv", "misure.tsv", "config.json", "impostazioni.jsonc", "eventi.ndjson", "rotto.json",
    "catalogo.xml", "rotto.xml", "Info.plist", "binario.plist", "libro3.epub", "libro2.epub", "protetto.epub",
    "messaggio.eml", "charset.eml", "riunioni.ics", "contatti.vcf", "grande.csv", "grande.json", "colori.html",
  )

  val Cities = listOf("Roma", "Milano", "Napoli", "Torino", "Palermo", "Genova", "Bologna", "Firenze", "Bari", "Catania")

  /** Una risorsa relativa, solo dentro [dir]: un percorso che esce dalla cartella non si legge. */
  fun resource(dir: File, path: String): java.io.InputStream? {
    val file = File(dir, path).canonicalFile
    if (!file.path.startsWith(dir.canonicalPath + File.separator) || !file.isFile) return null
    return file.inputStream()
  }
}
