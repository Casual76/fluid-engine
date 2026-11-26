/*
 * engine-scan (4.0, F8.17): lo scanner a tutto schermo, come `VNDocumentCameraViewController` di VisionKit: la
 * fotocamera di vetro, la revisione con la fila delle pagine e il ritaglio. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import dev.antigravity.fluidengine.ui.fluid.FluidAlertAction
import dev.antigravity.fluidengine.ui.fluid.FluidConfirmationDialog
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.LocalFluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import kotlinx.coroutines.launch

/** Le parole dello scanner, in italiano di serie. */
@Immutable
data class FluidScanStrings(
  val cancel: String = "Annulla",
  val save: String = "Salva",
  val saveCount: (Int) -> String = { n -> "Salva ($n)" },
  val shutter: String = "Scatta",
  val auto: String = "Automatico",
  val manual: String = "Manuale",
  val autoDescription: String = "Scatto automatico",
  val torch: String = "Flash",
  val torchOn: String = "Accendi la torcia",
  val torchOff: String = "Spegni la torcia",
  val filter: String = "Filtri",
  val filterNames: (FluidScanFilter) -> String = { f ->
    when (f) {
      FluidScanFilter.Grayscale -> "Scala di grigi"
      FluidScanFilter.BlackAndWhite -> "Bianco e nero"
      FluidScanFilter.Photo -> "Foto"
      else -> "Documento"
    }
  },
  val hintFind: String = "Inquadra un documento",
  val hintCloser: String = "Avvicinati",
  val hintSteady: String = "Tieni fermo",
  val hintCapturing: String = "Scansione…",
  val pages: (Int) -> String = { n -> if (n == 1) "1 pagina" else "$n pagine" },
  val openPages: String = "Rivedi le pagine",
  val pageOf: (Int, Int) -> String = { i, n -> "$i di $n" },
  val page: (Int, Int) -> String = { i, n -> "Pagina $i di $n" },
  val back: String = "Torna alla fotocamera",
  val crop: String = "Ritaglia",
  val rotate: String = "Ruota",
  val retake: String = "Riprendi",
  val delete: String = "Elimina",
  val done: String = "Fine",
  val reset: String = "Rileva i bordi",
  val wholePhoto: String = "Tutta la foto",
  val corners: List<String> = listOf("Angolo in alto a sinistra", "Angolo in alto a destra", "Angolo in basso a destra", "Angolo in basso a sinistra"),
  val moveLeft: String = "Sposta a sinistra",
  val moveRight: String = "Sposta a destra",
  val moveUp: String = "Sposta in alto",
  val moveDown: String = "Sposta in basso",
  val discardTitle: (Int) -> String = { n -> if (n == 1) "Eliminare la scansione?" else "Eliminare le $n scansioni?" },
  val discard: String = "Elimina",
  val keepScanning: String = "Continua a scansionare",
  val permissionTitle: String = "Per scansionare serve la fotocamera",
  val permissionAction: String = "Consenti la fotocamera",
  val unavailable: String = "La fotocamera non è disponibile",
  val saving: String = "Salvataggio…",
  val pageAdded: (Int) -> String = { n -> "Pagina $n scansionata" },
  val documentFound: String = "Documento trovato",
)

/** I tag dei test dello scanner. */
object FluidScanTags {
  const val Scanner = "fluidscan"
  const val Camera = "fluidscan:fotocamera"
  const val Shutter = "fluidscan:scatta"
  const val Save = "fluidscan:salva"
  const val Cancel = "fluidscan:annulla"
  const val Auto = "fluidscan:auto"
  const val Hint = "fluidscan:scritta"
  const val Thumbnail = "fluidscan:miniatura"
  const val Quad = "fluidscan:foglio"
  const val Review = "fluidscan:revisione"
  const val Strip = "fluidscan:fila"
  const val Crop = "fluidscan:ritaglia"
  const val Rotate = "fluidscan:ruota"
  const val Filter = "fluidscan:filtro"
  const val Delete = "fluidscan:elimina"
  const val Retake = "fluidscan:riprendi"
  const val CropEditor = "fluidscan:ritaglio"
  const val CropDone = "fluidscan:ritaglio:fine"

  fun stripPage(index: Int): String = "fluidscan:fila:$index"
  fun corner(index: Int): String = "fluidscan:angolo:$index"
  fun filter(filter: FluidScanFilter): String = "fluidscan:filtro:${filter.id}"
}

/** Dove sta lo scanner: la fotocamera, la revisione delle pagine, il ritaglio di una pagina. */
internal sealed interface ScanScreen {
  data object Camera : ScanScreen
  data class Review(val page: Int) : ScanScreen
  data class CropPage(val id: String, val returnTo: Int) : ScanScreen
  data class Retake(val id: String, val returnTo: Int) : ScanScreen
}

/**
 * Lo scanner di documenti a tutto schermo, come la scansione di Note e File di iOS (VisionKit):
 * - **la fotocamera di vetro**: il foglio trovato dal vivo, colorato; la scritta che guida («Inquadra un documento»,
 *   «Avvicinati», «Tieni fermo»); lo scatto da solo quando il foglio è fermo («Auto») o col tasto («Manuale»); la
 *   torcia e il filtro; la pagina raddrizzata che vola nella miniatura;
 * - **la revisione**: le pagine una per volta, la fila delle miniature da riordinare tenendo premuto, e Ritaglia,
 *   Ruota, Filtro, Riprendi, Elimina;
 * - **il ritaglio**: i quattro angoli da trascinare con la lente sotto il dito.
 *
 * «Salva» scrive la consegna ([FluidScanOptions.output]: il PDF, le immagini o tutte e due) e la passa a [onDone];
 * [onCancel] chiude senza (con le pagine già fatte chiede prima di buttarle). Le pagine restano in [state] finché l'app
 * non chiama [FluidScanState.clear].
 *
 * La fotocamera è [camera]: quella vera ([rememberFluidCameraXScanCamera]) o foto già scattate ([FluidStillScanCamera]).
 * Il movimento ridotto toglie il volo della pagina e il lampo dello scatto; TalkBack ha lo scatto, le pagine e gli
 * angoli come azioni.
 */
@Composable
fun FluidDocumentScanner(
  state: FluidScanState,
  camera: FluidScanCamera,
  onDone: (FluidScanResult) -> Unit,
  onCancel: () -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidScanStrings = FluidScanStrings(),
  /** Vero per aprire sulla revisione della prima pagina se [state] ne ha già (una scansione ripresa). */
  openReview: Boolean = false,
) {
  var screen by rememberSaveable(stateSaver = ScanScreenSaver) {
    mutableStateOf<ScanScreen>(if (openReview && state.pages.isNotEmpty()) ScanScreen.Review(0) else ScanScreen.Camera)
  }
  var auto by rememberSaveable { mutableStateOf(state.options.autoCapture) }
  var filter by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<FluidScanFilter, String>({ it.id }, { FluidScanFilter.of(it) })) {
    mutableStateOf(state.options.filter)
  }
  var confirmDiscard by remember { mutableStateOf(false) }
  var saving by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()
  val backdrop = rememberGlassBackdrop()
  val outerModal = LocalFluidGlassModalHostState.current
  val modal = outerModal ?: rememberFluidGlassModalHostState()
  val outerMenu = LocalFluidMorphMenuState.current
  val menu = outerMenu ?: rememberFluidMorphMenuState()
  val save: () -> Unit = {
    if (!saving && state.pages.isNotEmpty()) {
      saving = true
      scope.launch {
        try {
          onDone(state.export())
        } finally {
          saving = false
        }
      }
    }
  }
  val cancel: () -> Unit = { if (state.pages.isEmpty()) onCancel() else confirmDiscard = true }
  CompositionLocalProvider(LocalFluidGlassModalHostState provides modal, LocalFluidMorphMenuState provides menu) {
    Box(modifier.fillMaxSize().background(Color.Black).testTag(FluidScanTags.Scanner)) {
      when (val s = screen) {
        ScanScreen.Camera, is ScanScreen.Retake -> {
          val retake = s as? ScanScreen.Retake
          BackHandler { if (retake != null) screen = ScanScreen.Review(retake.returnTo) else cancel() }
          ScanCameraScreen(
            state = state, camera = camera, backdrop = backdrop, strings = strings,
            auto = auto, onAutoChange = { auto = it }, filter = filter,
            onFilterChange = { filter = it; state.setFilterForAll(it) },
            retaking = retake != null, saving = saving,
            onCaptured = { photo, quad ->
              if (retake != null) {
                state.retake(retake.id, photo, quad)
                screen = ScanScreen.Review(retake.returnTo)
              } else {
                state.add(photo, quad)?.let { page -> if (page.filter != filter) state.setFilter(page.id, filter) }
              }
            },
            onOpenPages = { if (state.pages.isNotEmpty()) screen = ScanScreen.Review(state.pages.lastIndex) },
            onSave = save, onCancel = { if (retake != null) screen = ScanScreen.Review(retake.returnTo) else cancel() },
          )
        }
        is ScanScreen.Review -> {
          BackHandler { screen = ScanScreen.Camera }
          ScanReviewScreen(
            state = state, initial = s.page, backdrop = backdrop, strings = strings, saving = saving,
            onBack = { screen = ScanScreen.Camera },
            onSave = save,
            onCrop = { id, at -> screen = ScanScreen.CropPage(id, at) },
            onRetake = { id, at -> screen = ScanScreen.Retake(id, at) },
            onEmpty = { screen = ScanScreen.Camera },
            onPageChange = { screen = ScanScreen.Review(it) },
          )
        }
        is ScanScreen.CropPage -> {
          BackHandler { screen = ScanScreen.Review(s.returnTo) }
          ScanCropScreen(
            state = state, id = s.id, backdrop = backdrop, strings = strings,
            onDone = { quad -> if (quad != null) state.setQuad(s.id, quad); screen = ScanScreen.Review(s.returnTo) },
          )
        }
      }
      FluidConfirmationDialog(
        visible = confirmDiscard,
        onDismissRequest = { confirmDiscard = false },
        title = strings.discardTitle(state.pages.size),
        actions = listOf(FluidAlertAction(strings.discard, {
          confirmDiscard = false
          state.clear()
          onCancel()
        }, FluidAlertAction.Emphasis.Destructive)),
        cancelLabel = strings.keepScanning,
      )
      if (outerModal == null) FluidGlassModalHost(modal, backdrop)
      if (outerMenu == null) FluidMorphMenuHost(state = menu, backdrop = backdrop)
    }
  }
}

private val ScanScreenSaver = androidx.compose.runtime.saveable.Saver<ScanScreen, String>(
  save = { s ->
    when (s) {
      ScanScreen.Camera -> "c"
      is ScanScreen.Review -> "r:${s.page}"
      is ScanScreen.CropPage -> "k:${s.returnTo}:${s.id}"
      is ScanScreen.Retake -> "t:${s.returnTo}:${s.id}"
    }
  },
  restore = { text ->
    val f = text.split(':')
    when (f[0]) {
      "r" -> ScanScreen.Review(f[1].toInt())
      "k" -> ScanScreen.CropPage(f[2], f[1].toInt())
      "t" -> ScanScreen.Retake(f[2], f[1].toInt())
      else -> ScanScreen.Camera
    }
  },
)
