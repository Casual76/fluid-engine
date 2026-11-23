package dev.antigravity.fluidengine.viewer.scene

import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerSignature
import java.text.NumberFormat

/** Licenze da aggiungere alla lista di [dev.antigravity.fluidengine.ui.fluid.fluidLicensesSection]. */
val FluidSceneViewerLicenses: List<FluidLicense> = listOf(
  FluidLicense("Filament, gltfio e Filament Utils", "Google", "Apache-2.0",
    "https://github.com/google/filament/blob/v1.71.5/LICENSE", "Modelli 3D, materiali e luce"),
)

/** Famiglia opt-in dei modelli 3D, scoperta dal registro dei visori. */
class FluidSceneFamily : FluidViewerFamily {
  override val id = "3d"
  override val types = listOf(
    type("glb", "GLB", setOf("model/gltf-binary"), FluidViewerSignature.ascii("glTF")),
    type("gltf", "glTF", setOf("model/gltf+json")),
    type("obj", "Wavefront OBJ", setOf("model/obj")),
    type("stl", "STL", setOf("model/stl", "application/sla", "application/vnd.ms-pki.stl")),
    type("usdz", "USDZ", setOf("model/vnd.usdz+zip")),
  )

  override suspend fun open(request: FluidViewerRequest): FluidViewerOpened {
    val model = readModel(request.source, request.type.id)
    val content = ScenePage(model, request.source.name, request.openWith).content
    val stats = model.stats
    val numbers = NumberFormat.getNumberInstance()
    val measures = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 6 }
    return FluidViewerOpened(content, listOf(
      FluidViewerDetail("Formato", request.type.label),
      FluidViewerDetail("Triangoli", numbers.format(stats.triangles)),
      FluidViewerDetail("Vertici", numbers.format(stats.vertices)),
      FluidViewerDetail("Materiali", numbers.format(stats.materials)),
      FluidViewerDetail("Texture", numbers.format(stats.textures), "Modello"),
      FluidViewerDetail("Animazioni", numbers.format(stats.animations), "Modello"),
      FluidViewerDetail("Dimensioni", listOf(stats.width, stats.height, stats.depth).joinToString(" × ", transform = measures::format), "Modello"),
    ))
  }

  private fun type(id: String, label: String, mimes: Set<String>, signature: FluidViewerSignature? = null) =
    FluidViewerFamilyType(id, label, setOf(id), mimes, signature, FluidFileKind.Other, priority = 20)

}

private class ScenePage(model: FluidSceneModel, name: String, openWith: (() -> Unit)?) {
  private var playing by mutableStateOf(false)
  private var animation by mutableIntStateOf(0)
  private var reset by mutableIntStateOf(0)
  private var failure by mutableStateOf(false)
  private val extension = sceneViewerExtension?.create(model)
  private var frameReady by mutableStateOf(false)

  val content = FluidViewerContent.Custom(
    actions = {
      if (!failure) {
        button("3d.frame", Icons.Default.CenterFocusStrong, "Inquadra", { reset++ }, FluidToolbarPlacement.BottomBar)
        if (model.stats.animations > 0) button("3d.play", if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
          if (playing) "Pausa" else "Riproduci", { playing = !playing }, FluidToolbarPlacement.BottomBar)
        if (model.stats.animations > 1) button("3d.animation", Icons.Default.SkipNext,
          "Animazione successiva", { animation = (animation + 1) % model.stats.animations }, FluidToolbarPlacement.BottomBar)
        extension?.actions(this)
      }
    },
    firstFrameReady = { frameReady || failure },
    draw = {
    if (failure) {
      Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(FluidScreenDefaults.HorizontalPadding)) {
          Text("Il modello non si apre su questo dispositivo", style = MaterialTheme.typography.bodyLarge)
          if (openWith != null) FluidButton("Apri con…", openWith)
        }
      }
    } else {
      val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
      AndroidView(
        factory = { context ->
          frameReady = false
          SceneTexture(context, model, onFirstFrame = { frameReady = true }) { error ->
            Log.w("FluidSceneViewer", "Filament non può aprire la scena", error)
            failure = true
          }
        },
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Modello 3D, $name" },
        onReset = null,
        onRelease = SceneTexture::dispose,
        update = { it.update(active && extension?.overlayVisible != true &&
          lifecycle.isAtLeast(Lifecycle.State.RESUMED), playing, animation, reset) },
      )
      extension?.Overlay(active)
    }
    },
  )
}
