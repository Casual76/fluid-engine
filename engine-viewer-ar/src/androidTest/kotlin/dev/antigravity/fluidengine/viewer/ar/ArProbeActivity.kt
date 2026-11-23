package dev.antigravity.fluidengine.viewer.ar

import android.os.Bundle
import android.os.ParcelFileDescriptor
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerSource

class ArProbeActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      var dark by rememberSaveable { mutableStateOf(false) }
      var open by rememberSaveable { mutableStateOf(false) }
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        if (open) {
          BackHandler { open = false }
          ProbeModel { open = false }
        } else {
          val gap = FluidScreenDefaults.HorizontalPadding
          Box(Modifier.fillMaxSize().safeDrawingPadding().padding(gap), contentAlignment = Alignment.Center) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
              FluidButton(if (dark) "Passa al tema chiaro" else "Passa al tema scuro", { dark = !dark })
              FluidButton("Apri il modello", { open = true })
            }
          }
        }
      }
    }
  }
}

@Composable
private fun ProbeModel(back: () -> Unit) {
  val context = LocalContext.current
  var result by remember { mutableStateOf<FluidViewerDocument?>(null) }
  LaunchedEffect(context) {
    result = FluidViewerRegistry.open(context, object : FluidViewerSource {
      override val name = "Piramide.obj"
      override suspend fun open() = context.assets.open("Piramide.obj")
      override suspend fun seekable(): ParcelFileDescriptor = error("La piramide si legge come flusso")
    })
  }
  val document = result
  DisposableEffect(document) { onDispose { document?.close() } }
  if (document == null) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Apertura del modello…", style = MaterialTheme.typography.bodyMedium)
    }
  } else {
    FluidFileViewer(count = 1, content = { document.content }, onBack = back, onDismissRequest = back)
  }
}
