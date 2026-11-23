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
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFileKind
import dev.antigravity.fluidengine.ui.fluid.FluidLicense
import dev.antigravity.fluidengine.ui.fluid.FluidScreenDefaults
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.viewerTypeActions
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

  /** F6C.17 · Il modello disegnato a tre quarti con l'ombra a terra ([sceneThumbnail]); se non si legge, il simbolo del 3D. */
  override suspend fun thumbnail(request: FluidViewerRequest, sizePx: Int) = sceneThumbnail(request, sizePx)

  private fun type(id: String, label: String, mimes: Set<String>, signature: FluidViewerSignature? = null) =
    FluidViewerFamilyType(id, label, setOf(id), mimes, signature, FluidFileKind.Other, priority = 20)

}

/**
 * La pagina di un modello 3D: la superficie di Filament ([surface]) e la barra del visore. F6C.20: la barra tiene tre
 * vetri (R5 B11: «•••», il centro, condividi), quindi al centro c'è un comando solo: «AR» dell'estensione quando lo
 * mostra ([FluidSceneViewerControls.showsAction]), se no «Riproduci/Pausa» per un modello animato o «Inquadra» per uno
 * fermo. Il resto sta in «•••»: «Inquadra», le animazioni per nome (con almeno due: prendono il posto di «Animazione
 * successiva») e la rotazione automatica della camera ([SceneSpin]).
 */
internal class ScenePage(
  private val model: FluidSceneModel,
  name: String,
  openWith: (() -> Unit)?,
  surface: SceneSurfaceFactory = FilamentSceneSurface,
  private val extension: FluidSceneViewerControls? = sceneViewerExtension?.create(model),
) {
  private var playing by mutableStateOf(false)
  private var animation by mutableIntStateOf(0)
  private var reset by mutableIntStateOf(0)
  private var failure by mutableStateOf(false)
  private var autoRotate by mutableStateOf(false)
  private var frameReady by mutableStateOf(false)

  private val animated get() = model.stats.animations > 0
  private val playLabel get() = if (playing) "Pausa" else "Riproduci"

  /** Il nome di un'animazione per il menu: quello del file, o «Animazione N». */
  private fun animationName(index: Int): String = model.animationNames.getOrNull(index) ?: "Animazione ${index + 1}"

  /**
   * Le azioni del tipo in «•••», lette quando il menu si apre: la clip e la rotazione di quel momento, e i comandi che
   * non hanno il centro della barra ([extensionBar]: il centro è dell'estensione).
   */
  private fun typeActions(extensionBar: Boolean): List<FluidContextAction> = listOfNotNull(
    if (extensionBar && animated) FluidContextAction(playLabel) { playing = !playing } else null,
    if (extensionBar || animated) FluidContextAction("Inquadra") { reset++ } else null,
    if (model.stats.animations < 2) null else FluidContextAction(
      "Animazioni", subtitle = animationName(animation),
      children = List(model.stats.animations) { index ->
        // Scegliere un'animazione la fa vedere: da ferma la posa non cambierebbe.
        FluidContextAction(animationName(index), selected = index == animation) {
          animation = index
          playing = true
        }
      },
    ) {},
    FluidContextAction("Rotazione automatica", selected = autoRotate) { autoRotate = !autoRotate },
  )

  val content = FluidViewerContent.Custom(
    actions = {
      if (!failure) {
        val extensionBar = extension?.showsAction == true
        if (!extensionBar) {
          if (animated) button("3d.play", if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, playLabel,
            { playing = !playing }, FluidToolbarPlacement.BottomBar)
          else button("3d.frame", Icons.Default.CenterFocusStrong, "Inquadra", { reset++ }, FluidToolbarPlacement.BottomBar)
        }
        viewerTypeActions { typeActions(extensionBar) }
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
          surface.create(context, model, { frameReady = true }) { error ->
            Log.w("FluidSceneViewer", "Filament non può aprire la scena", error)
            failure = true
          }
        },
        modifier = Modifier.fillMaxSize().semantics { contentDescription = "Modello 3D, $name" },
        onReset = null,
        onRelease = { (it as SceneSurface).dispose() },
        update = { (it as SceneSurface).update(active && extension?.overlayVisible != true &&
          lifecycle.isAtLeast(Lifecycle.State.RESUMED), playing, animation, reset, autoRotate) },
      )
      extension?.Overlay(active)
    }
    },
  )
}
