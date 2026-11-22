package dev.antigravity.fluidengine.viewer.graphics

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidEngineLicenses
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfo
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoRow
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoSection
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource

private val files = listOf(
  "studio.heic",
  "studio.avif",
  "camera.dng",
  "camera-pixel.dng",
  "camera.cr2",
  "camera.cr3",
  "camera.nef",
  "camera.arw",
  "studio-pages.tiff",
  "composito.psd",
  "miniatura.psd",
  "tracce.svg",
  "tracce.svgz",
  "studio.ttf",
  "studio.otf",
  "studio.ttc",
  "studio-variable.ttf",
  "studio.woff",
  "studio.woff2",
)

class CorpusActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    setContent {
      var dark by rememberSaveable { mutableStateOf(false) }
      var file by rememberSaveable { mutableStateOf<String?>(null) }
      var chooser by rememberSaveable { mutableStateOf(false) }
      var largeText by rememberSaveable { mutableStateOf(false) }
      var compact by rememberSaveable { mutableStateOf(false) }
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val selected = file
        if (selected != null && !chooser) {
          BackHandler { file = null }
          val displayDensity = LocalDensity.current.density
          CompositionLocalProvider(LocalDensity provides Density(displayDensity, if (largeText) 2f else 1f)) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
              Box(Modifier.widthIn(max = if (compact) 400.dp else Dp.Infinity).fillMaxWidth()) {
                FilePage(selected, { file = null }) { chooser = true }
              }
            }
          }
        } else {
          LazyColumn(Modifier.fillMaxSize().padding(
            horizontal = FluidScreenDefaults.HorizontalPadding,
            vertical = FluidScreenDefaults.topBarHeight(),
          ), verticalArrangement = Arrangement.spacedBy(FluidScreenDefaults.HorizontalPadding)) {
            item { FluidButton(if (dark) "Passa al tema chiaro" else "Passa al tema scuro", { dark = !dark }) }
            item { FluidButton(if (largeText) "Scala testo 1,0" else "Scala testo 2,0", { largeText = !largeText }) }
            item { FluidButton(if (compact) "Larghezza del tablet" else "Larghezza telefono · 400 dp", { compact = !compact }) }
            if (chooser) item { FluidButton("Torna ai file", { chooser = false; file = null }) }
            item {
              FluidListGroup {
                files.forEachIndexed { index, name ->
                  if (index != 0) FluidListDivider()
                  FluidListRow(title = name, subtitle = "", onClick = { file = name; chooser = false })
                }
              }
            }
            fluidLicensesSection(licences = FluidEngineLicenses + FluidGraphicsViewerLicenses)
          }
        }
      }
    }
  }
}

@Composable
private fun FilePage(name: String, dismiss: () -> Unit, openWith: () -> Unit) {
  val context = LocalContext.current
  var result by remember(name) { mutableStateOf<FluidViewerDocument?>(null) }
  LaunchedEffect(name) { result = FluidViewerRegistry.open(context, context.corpusSource(name), openWith = openWith) }
  val document = result
  DisposableEffect(document) { onDispose { document?.close() } }
  if (document == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    return
  }
  val details = remember(document) {
    val sections = document.details.filter { it.section != null && it.section != "Lettura" }.groupBy { it.section }
    val read = document.details.filter { it.section == "Lettura" }.map { FluidViewerInfoRow(it.label, it.value) } +
      listOfNotNull(document.failure?.message?.let { FluidViewerInfoRow("Anteprima", it) })
    FluidViewerInfo(document.file.info, subtitle = document.type.label,
      facts = document.details.filter { it.section == null }.map { "${it.label} · ${it.value}" } +
        listOfNotNull(document.failure?.message),
      sections = sections.map { (title, rows) ->
        FluidViewerInfoSection(requireNotNull(title), rows.map { FluidViewerInfoRow(it.label, it.value) })
      },
      reading = read.takeIf { it.isNotEmpty() }?.let { FluidViewerInfoSection("Lettura", it) },
    )
  }
  FluidViewerInfoProvider(info = { details }) {
    FluidFileViewer(count = 1, content = { document.content }, onBack = dismiss, onDismissRequest = dismiss)
  }
}

internal fun Context.corpusSource(name: String): FluidViewerSource =
  FluidViewerSource.of(name, cacheDir) { assets.open("corpus/$name") }
