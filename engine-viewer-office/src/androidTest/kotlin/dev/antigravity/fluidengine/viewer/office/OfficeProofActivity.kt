package dev.antigravity.fluidengine.viewer.office

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.*
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Test APK only. Every tap opens a real corpus asset through the public registry. */
class OfficeProofActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      FluidTheme(EngineSettings()) {
        var name by remember { mutableStateOf<String?>(null) }
        val names = remember {
          assets
            .list("")
            .orEmpty()
            .filter {
              it.substringAfterLast('.') in
                setOf("docx", "odt", "rtf", "pptx", "odp", "xlsx", "ods")
            }
            .sorted()
        }
        val selected = name
        if (selected == null)
          Surface(Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = WindowInsets.safeDrawing.asPaddingValues()) {
              items(names) { file -> TextButton(onClick = { name = file }) { Text(file) } }
            }
          }
        else
          key(selected) {
            var doc by remember { mutableStateOf<FluidViewerDocument?>(null) }
            DisposableEffect(Unit) { onDispose { doc?.close() } }
            LaunchedEffect(Unit) {
              val path =
                withContext(Dispatchers.IO) {
                  File(cacheDir, selected).also { file ->
                    assets.open(selected).use { input -> file.outputStream().use(input::copyTo) }
                  }
                }
              val opened =
                FluidViewerRegistry.open(
                  this@OfficeProofActivity,
                  FluidViewerSource.of(path, deleteOnClose = true),
                )
              doc = opened
            }
            doc?.let { opened ->
              FluidViewerInfoProvider({ opened.officeInfo() }) {
                FluidFileViewer(count = 1, content = { opened.content }, onBack = { name = null })
              }
            }
          }
      }
    }
  }
}
