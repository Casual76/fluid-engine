package dev.antigravity.fluidengine.viewer.archive.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidChip
import dev.antigravity.fluidengine.ui.fluid.FluidEngineLicenses
import dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerInfoProvider
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import dev.antigravity.fluidengine.ui.theme.FluidListRow
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import dev.antigravity.fluidengine.viewer.archive.FluidArchiveFamily
import dev.antigravity.fluidengine.viewer.archive.FluidArchiveLicenses
import dev.antigravity.fluidengine.viewer.archive.fluidArchiveViewerInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

class ArchiveQaActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    // Exercise automatic registration before installing the app-owned extraction action.
    check(FluidViewerRegistry.families.any { it.id == "archive" })
    FluidViewerRegistry.install(FluidArchiveFamily(onExtractAll = { extraction ->
      val target = withContext(Dispatchers.IO) { Files.createTempDirectory(cacheDir.toPath(), "qa-extract-").toFile() }
      try {
        extraction.writeFiles { entry ->
          val output = File(target, entry.path).canonicalFile
          check(output.toPath().startsWith(target.canonicalFile.toPath()))
          output.parentFile?.mkdirs()
          output.outputStream()
        }
      } finally {
        withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { target.deleteRecursively() }
      }
    }))
    setContent {
      var dark by rememberSaveable { mutableStateOf(false) }
      var largeText by rememberSaveable { mutableStateOf(false) }
      var selected by rememberSaveable { mutableStateOf<String?>(null) }
      val deviceDensity = LocalDensity.current.density
      CompositionLocalProvider(LocalDensity provides Density(deviceDensity, if (largeText) 2f else 1f)) {
        FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
          val name = selected
          if (name == null) choose(dark, { dark = !dark }, largeText, { largeText = !largeText }, { selected = it })
          else preview(name) { selected = null }
        }
      }
    }
  }

  @Composable
  private fun choose(dark: Boolean, toggle: () -> Unit, largeText: Boolean, toggleText: () -> Unit, open: (String) -> Unit) {
    val files = remember {
      val primary = listOf("progetto.zip", "zip-aes.zip", "7z-aes.7z", "7z-intestazioni.7z", "rar5-protetto.rar")
      primary + assets.list("").orEmpty().map { it.removeSuffix(".fixture") }
        .filter { it !in primary && File(it).extension in archiveExtensions }.sorted()
    }
    LazyColumn(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).safeDrawingPadding(),
      contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      item {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Prova archivi", style = MaterialTheme.typography.headlineSmall)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FluidChip("Scuro", dark, toggle)
            FluidChip("Testo 2×", largeText, toggleText)
          }
        }
      }
      item {
        FluidListGroup {
          files.forEachIndexed { i, name ->
            if (i != 0) FluidListDivider()
            FluidListRow(name, "", onClick = { open(name) })
          }
        }
      }
      fluidLicensesSection(licences = FluidEngineLicenses + FluidArchiveLicenses)
    }
  }

  @Composable
  private fun preview(name: String, close: () -> Unit) {
    var document by remember(name) { mutableStateOf<FluidViewerDocument?>(null) }
    var failed by remember(name) { mutableStateOf(false) }
    BackHandler(onBack = close)
    LaunchedEffect(name) {
      var pending: FluidViewerDocument? = null
      var temp: File? = null
      try {
        withContext(Dispatchers.IO) {
          val file = File.createTempFile("qa-input-", ".fixture", cacheDir)
          temp = file
          val asset = if (name.endsWith(".gz")) "$name.fixture" else name
          assets.open(asset).use { input -> file.outputStream().use { input.copyTo(it) } }
          pending = FluidViewerRegistry.open(applicationContext, FluidViewerSource.of(file, name, deleteOnClose = true))
        }
        document = pending
        pending = null
        temp = null
      } catch (_: java.io.IOException) {
        failed = true
      } finally {
        pending?.close()
        temp?.delete()
      }
    }
    val opened = document
    DisposableEffect(opened) { onDispose { opened?.close() } }
    if (opened == null) {
      Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        if (failed) Text("File di prova non leggibile") else CircularProgressIndicator()
      }
    } else {
      FluidViewerInfoProvider(info = { opened.fluidArchiveViewerInfo() }) {
        FluidFileViewer(count = 1, content = { opened.content }, onBack = { onBackPressedDispatcher.onBackPressed() })
      }
    }
  }
}

private val archiveExtensions = setOf("zip", "7z", "rar", "tar", "tgz", "gz", "xz", "zst")
