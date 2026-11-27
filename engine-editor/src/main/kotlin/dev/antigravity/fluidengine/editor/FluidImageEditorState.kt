package dev.antigravity.fluidengine.editor

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Le schede dell'editor, come la barra in basso di Foto. Non un enum (la regola 3.11): domani «Filtri».
 */
class FluidEditorMode private constructor(val id: String) {
  override fun toString() = "FluidEditorMode($id)"

  companion object {
    /** «Regola»: la ruota delle regolazioni e il righello. */
    val Adjust = FluidEditorMode("adjust")
    /** «Ritaglia»: la cornice, le proporzioni, la rotazione e il raddrizzamento. */
    val Crop = FluidEditorMode("crop")

    internal fun of(id: String?) = if (id == Crop.id) Crop else Adjust
  }
}

/**
 * Lo stato dell'editor: la ricetta di adesso ([edit]), la cronologia per annulla e ripristina, la scheda, la regolazione
 * scelta, il confronto. Si crea con [rememberFluidImageEditorState]; l'app lo tiene per tutta la modifica (sopravvive
 * alla rotazione dello schermo: la ricetta, la scheda e la regolazione si salvano, la cronologia no).
 *
 * **Durante un gesto** ([edit] cambia a ogni fotogramma: il righello, una maniglia, due dita) la cronologia non si
 * tocca; il passo si scrive quando il dito si alza. Cosi' «Annulla» torna al gesto prima, non al fotogramma prima.
 */
@Stable
class FluidImageEditorState internal constructor(
  /** La foto da modificare. */
  val source: FluidEditorSource,
  /** La ricetta con cui l'editor si e' aperto: «Annulla» in alto la ridà all'app senza modifiche. */
  val initial: FluidImageEdit,
  restored: FluidImageEdit,
  private val appContext: Context,
) {
  private val history = FluidEditHistory(restored)
  private var historyVersion by mutableIntStateOf(0)
  private var gestureBase: FluidImageEdit? = null

  /** La ricetta di adesso, anche a meta' di un gesto. Letta nel disegno, non ricompone l'editor a ogni fotogramma. */
  var edit: FluidImageEdit by mutableStateOf(restored)
    private set

  /** La scheda di adesso. */
  var mode: FluidEditorMode by mutableStateOf(FluidEditorMode.Adjust)

  /** La regolazione sotto il righello (quella al centro della ruota); null se al centro c'e' «Auto». */
  var selected: FluidAdjustment? by mutableStateOf<FluidAdjustment?>(FluidAdjustment.Exposure)

  /** Vero mentre il dito tiene premuta la foto: si vede l'originale. */
  var showingOriginal: Boolean by mutableStateOf(false)
    internal set

  /** Il confronto a tendina: a sinistra senza regolazioni, a destra con. */
  var splitCompare: Boolean by mutableStateOf(false)

  /** Vero mentre e' aperta la fila delle proporzioni (in «Ritaglia»). */
  var aspectPanel: Boolean by mutableStateOf(false)

  /** Vero mentre un dito muove qualcosa (il righello, la ruota, la cornice): l'anteprima e' quella veloce. */
  var interacting: Boolean by mutableStateOf(false)
    internal set

  internal var image: EditorImage? by mutableStateOf<EditorImage?>(null)

  /** Perche' la foto non si e' aperta, o null. L'editor mostra «Riprova» ([FluidImageEditorStrings.retry]). */
  var loadError: Throwable? by mutableStateOf<Throwable?>(null)
    internal set

  /** Quante volte si e' chiesta l'apertura: «Riprova» la richiede ([retryLoad]). */
  internal var loadAttempt: Int by mutableIntStateOf(0)
    private set

  /** Vero quando la foto e' pronta (l'anteprima e' caricata). */
  val isReady: Boolean get() = image != null

  /** Le misure vere della foto (dritta), o null finche' non e' pronta. */
  val sourceSize: Pair<Int, Int>? get() = image?.let { it.sourceWidth to it.sourceHeight }

  val canUndo: Boolean get() { historyVersion; return history.canUndo }
  val canRedo: Boolean get() { historyVersion; return history.canRedo }

  /** Vero se la ricetta e' diversa da quella di partenza: «Annulla» chiede conferma. */
  val hasChanges: Boolean get() = edit != initial

  /** Quante volte si e' girata a sinistra o specchiata la foto (per l'animazione). */
  internal var turnEvents by mutableIntStateOf(0)
  internal var lastTurnFlip = false

  // --- Le regolazioni --------------------------------------------------------------------------------------------

  /** Cambia [adjustment] durante un gesto (il righello): niente cronologia finche' non arriva [commit]. */
  fun setAdjustment(adjustment: FluidAdjustment, value: Float) {
    edit = edit.with(adjustment, value)
  }

  /** «Auto»: acceso aggiunge la proposta per questa foto, spento la toglie lasciando le modifiche a mano. */
  fun toggleAuto() {
    val img = image ?: return
    edit = edit.withAuto(if (edit.isAuto) null else img.auto)
    commit()
  }

  /** Scrive il passo nella cronologia (a gesto finito, o dopo un tasto). */
  fun commit() {
    gestureBase = null
    if (history.commit(edit)) historyVersion++
  }

  fun undo() {
    history.undo()?.let { edit = it; historyVersion++ }
  }

  fun redo() {
    history.redo()?.let { edit = it; historyVersion++ }
  }

  /** «Ripristina originale»: la foto com'era, come passo che si puo' annullare. */
  fun revertToOriginal() {
    edit = FluidImageEdit.Original
    commit()
  }

  // --- La geometria ------------------------------------------------------------------------------------------------

  private fun size(): Pair<Int, Int>? = image?.let { it.sourceWidth to it.sourceHeight }

  /** Gira a sinistra di 90°, come il tasto di Foto. */
  fun rotateLeft() {
    edit = edit.copy(geometry = edit.geometry.rotatedLeft())
    lastTurnFlip = false
    turnEvents++
    commit()
  }

  /** Lo specchio orizzontale. */
  fun flip() {
    edit = edit.copy(geometry = edit.geometry.flippedHorizontally())
    lastTurnFlip = true
    turnEvents++
    commit()
  }

  /** Blocca le proporzioni [aspect]: il ritaglio diventa il piu' grande con quelle proporzioni. */
  fun setAspect(aspect: FluidCropAspect) {
    val (sw, sh) = size() ?: return
    var g = edit.geometry.copy(aspect = aspect)
    val (ow, oh) = g.orientedSize(sw, sh)
    aspect.ratio(ow.toFloat() / oh)?.let { g = g.largestWithRatio(it, sw, sh) }
    edit = edit.copy(geometry = g)
    commit()
  }

  /** Gira le proporzioni scelte fra verticale e orizzontale (il tasto accanto alla fila di Foto). */
  fun setAspectPortrait(portrait: Boolean) {
    val (sw, sh) = size() ?: return
    val g = edit.geometry
    val (ow, oh) = g.orientedSize(sw, sh)
    val ratio = ow.toFloat() / oh
    if (g.aspect.isFreeform || g.aspect.width == g.aspect.height) return
    if (g.aspect.isPortrait(ratio) != portrait) {
      val turned = if (g.aspect.width < 0) FluidCropAspect.custom(oh, ow) else g.aspect.rotated()
      setAspect(turned)
    }
  }

  /** «Reimposta»: il ritaglio intero, dritto, senza rotazioni e senza specchio. */
  fun resetGeometry() {
    edit = edit.copy(geometry = FluidCropGeometry())
    commit()
  }

  /** Il raddrizzamento durante un gesto: il ritaglio si stringe dalla cornice di quando il gesto e' cominciato. */
  fun setStraighten(degrees: Float) {
    val (sw, sh) = size() ?: return
    val base = (gestureBase ?: edit.also { gestureBase = it }).geometry
    edit = edit.copy(geometry = base.copy(angle = degrees.coerceIn(-45f, 45f)).fitted(sw, sh))
  }

  internal fun setGeometryLive(geometry: FluidCropGeometry) {
    edit = edit.copy(geometry = geometry)
  }

  internal fun beginGesture() {
    interacting = true
  }

  internal fun endGesture() {
    interacting = false
    commit()
  }

  // --- Il risultato --------------------------------------------------------------------------------------------------

  /**
   * La foto modificata in memoria, col lato lungo al piu' [maxSide], per un'anteprima veloce (il passaggio al disegno di
   * `engine-ink` usa invece un file PNG nella cache: [export]). Fuori dal thread principale; la bitmap e' di chi chiama.
   */
  suspend fun render(maxSide: Int = 2048): Bitmap = FluidImageExport.render(appContext, source, edit, maxSide)

  /**
   * Salva la foto modificata a piena risoluzione in [target] (un file nuovo: l'originale non si tocca). La posizione GPS
   * dello scatto di serie resta, come Foto di iOS, e `keepLocation = false` la toglie (R3); un salvataggio che non riesce
   * lancia `IOException`.
   */
  suspend fun export(target: File, format: FluidImageExport.Format = FluidImageExport.Format.jpeg(),
    keepLocation: Boolean = FluidImageExport.KeepLocationByDefault): FluidImageExport.Result =
    FluidImageExport.export(appContext, source, edit, target, format, keepLocation = keepLocation)

  /** Lo stesso in un indirizzo aperto in scrittura (MediaStore, «Salva come»). */
  suspend fun export(target: Uri, format: FluidImageExport.Format = FluidImageExport.Format.jpeg(),
    keepLocation: Boolean = FluidImageExport.KeepLocationByDefault): FluidImageExport.Result =
    FluidImageExport.export(appContext, source, edit, target, format, keepLocation = keepLocation)

  internal suspend fun load(maxSide: Int) {
    if (image != null) return
    try {
      image = withContext(Dispatchers.IO) {
        EditorImage.load(appContext, source, maxSide).also { img ->
          // Le cose che servono al primo fotogramma si preparano qui, non nel disegno.
          img.definition
          img.definitionBitmap
        }
      }
    } catch (e: CancellationException) {
      // R1: l'editor che esce mentre la foto si legge non e' un errore di apertura.
      throw e
    } catch (e: Exception) {
      loadError = e
    }
  }

  /** «Riprova» dopo un errore di apertura (il permesso tornato, il file arrivato): l'apertura riparte. */
  internal fun retryLoad() {
    if (loadError == null) return
    loadError = null
    loadAttempt++
  }

  internal fun restoreUi(mode: String?, selected: String?) {
    this.mode = FluidEditorMode.of(mode)
    this.selected = if (selected == AutoId) null else selected?.let { FluidAdjustment.byId(it) } ?: FluidAdjustment.Exposure
  }

  internal companion object {
    const val AutoId = "auto"
  }
}

/**
 * Lo stato di un editor per la foto [source], aperto con la ricetta [initial] (di serie la foto com'e'; una ricetta
 * salvata riapre la modifica). L'anteprima si carica fuori dal thread principale, col lato lungo grande quanto lo
 * schermo (al piu' [previewMaxSide]).
 */
@Composable
fun rememberFluidImageEditorState(
  source: FluidEditorSource,
  initial: FluidImageEdit = FluidImageEdit.Original,
  previewMaxSide: Int = 0,
): FluidImageEditorState {
  val context = LocalContext.current.applicationContext
  val config = LocalConfiguration.current
  val density = LocalDensity.current.density
  val screen = max(config.screenWidthDp, config.screenHeightDp) * density
  val maxSide = if (previewMaxSide > 0) previewMaxSide else screen.roundToInt().coerceIn(1024, 2560)
  // R1: la chiave e' la sorgente intera (l'indirizzo, o la bitmap stessa): prima era il solo indirizzo, null per ogni
  // bitmap, e una seconda bitmap nello stesso posto si apriva con la ricetta della prima. Alla ricreazione il valore
  // salvato torna comunque (le chiavi non si salvano).
  var saved by rememberSaveable(source) { mutableStateOf(initial.encode()) }
  var savedMode by rememberSaveable(source) { mutableStateOf(FluidEditorMode.Adjust.id) }
  var savedSelected by rememberSaveable(source) { mutableStateOf(FluidAdjustment.Exposure.id) }
  val state = remember(source) {
    FluidImageEditorState(source, initial, FluidImageEdit.decode(saved) ?: initial, context).also {
      it.restoreUi(savedMode, savedSelected)
    }
  }
  LaunchedEffect(state, state.loadAttempt) { state.load(maxSide) }
  // Quello che sopravvive alla ricreazione si scrive a ogni passo, non a ogni fotogramma.
  LaunchedEffect(state) {
    androidx.compose.runtime.snapshotFlow { Triple(if (state.interacting) null else state.edit, state.mode.id, state.selected?.id) }
      .collect { (e, m, s) ->
        if (e != null) saved = e.encode()
        savedMode = m
        savedSelected = s ?: FluidImageEditorState.AutoId
      }
  }
  return state
}
