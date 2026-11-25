package dev.antigravity.fluidengine.sharein

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.WindowCompat
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidChoiceRow
import dev.antigravity.fluidengine.ui.fluid.FluidFileTile
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidProgressBar
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPanel
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Link
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.rememberFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidListDivider
import dev.antigravity.fluidengine.ui.theme.FluidListGroup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/** Dove l'app puo' mettere cio' che arriva: un quaderno, un album, una cartella del NAS. */
@Immutable
class FluidShareInDestination(val id: String, val title: String, val subtitle: String = "", val icon: ImageVector? = null)

/** Cio' che l'app salva: il testo o il link, e le copie locali dei file che sono arrivati bene. */
@Immutable
class FluidShareInContent(
  val text: String?,
  val link: String?,
  val subject: String?,
  val files: List<File>,
  /** Vero per il testo selezionato in un'altra app (`PROCESS_TEXT`). */
  val selection: Boolean,
)

/** A che punto e' l'arrivo. */
enum class FluidShareInPhase { Reading, Copying, Ready, Saving, Saved, Nothing }

/**
 * Lo stato di un arrivo (4.0, F8.10): legge l'intent e **copia subito** i file, su `Dispatchers.IO`, appena si compone
 * ([rememberFluidShareInState]). Il foglio lo mostra; l'app lo legge per salvare.
 */
@Stable
class FluidShareInState internal constructor(val policy: FluidShareInPolicy) {
  /** Cosa e' arrivato; null finche' non e' letto. */
  var incoming: FluidIncoming? by mutableStateOf(null)
    internal set

  /** I file dopo la copia (quelli arrivati e quelli scartati, col motivo). */
  var copies: List<FluidCopiedFile> by mutableStateOf(emptyList())
    internal set

  /** L'avanzamento della copia; null fuori dalla copia. */
  var progress: FluidShareInProgress? by mutableStateOf(null)
    internal set

  var phase: FluidShareInPhase by mutableStateOf(FluidShareInPhase.Reading)
    internal set

  /** Il contenuto da salvare, quando c'e' qualcosa da salvare. */
  val content: FluidShareInContent?
    get() = when (val now = incoming) {
      is FluidIncoming.Text -> if (policy.acceptText) FluidShareInContent(now.text, null, now.subject, emptyList(), now.selection) else null
      is FluidIncoming.Link -> if (policy.acceptText) FluidShareInContent(null, now.url, now.title, emptyList(), false) else null
      is FluidIncoming.Files -> copies.mapNotNull { it.file?.takeIf { _ -> it.ok } }.takeIf { it.isNotEmpty() }
        ?.let { FluidShareInContent(now.text, null, null, it, false) }
      else -> null
    }

  /** I file scartati, col motivo. */
  val rejected: List<FluidCopiedFile> get() = copies.filter { !it.ok }

  /** Legge e copia. Chiamato una volta da [rememberFluidShareInState]. */
  suspend fun receive(context: Context, intent: Intent?) {
    phase = FluidShareInPhase.Reading
    val read = FluidShareIn.read(context, intent, policy)
    incoming = read
    if (read is FluidIncoming.Files) {
      phase = FluidShareInPhase.Copying
      copies = FluidShareIn.copy(context, read.files, policy) { progress = it }
      progress = null
    }
    phase = if (content != null) FluidShareInPhase.Ready else FluidShareInPhase.Nothing
  }
}

/** Lo stato dell'arrivo di [intent], che comincia subito a leggere e a copiare. */
@Composable
fun rememberFluidShareInState(intent: Intent?, policy: FluidShareInPolicy = FluidShareInPolicy()): FluidShareInState {
  val context = LocalContext.current
  val state = remember(intent, policy) { FluidShareInState(policy) }
  LaunchedEffect(state) { state.receive(context.applicationContext, intent) }
  return state
}

/** Le parole del foglio, di serie in italiano. */
@Immutable
data class FluidShareInLabels(
  val cancel: String = "Annulla",
  val save: String = "Salva",
  val destination: String = "Dove",
  val reading: String = "Leggo cosa è arrivato…",
  val copying: String = "Copio %1\$d di %2\$d…",
  val saving: String = "Salvo…",
  val saved: String = "Salvato",
  val nothing: String = "Niente da salvare qui.",
  val textSelection: String = "Testo selezionato",
  val rejected: String = "Non arrivati: %s",
  val tooLarge: String = "troppo grande",
  val tooMany: String = "troppi file",
  val typeNotAccepted: String = "tipo non accettato",
  val unreadable: String = "non si legge",
  val unsafe: String = "non sicuro",
  val notAShare: String = "non è una condivisione",
  val empty: String = "vuoto",
  val failed: String = "Non si è salvato",
)

/** I tag dei test e delle riprese. */
object FluidShareInTags {
  const val Sheet = "fluid-share-in:foglio"
  const val Scrim = "fluid-share-in:velo"
  const val Save = "fluid-share-in:salva"
  const val Cancel = "fluid-share-in:annulla"
  const val Preview = "fluid-share-in:anteprima"
  const val Status = "fluid-share-in:stato"
  fun destination(id: String): String = "fluid-share-in:dove:$id"
}

/**
 * Il foglio degli arrivi (4.0, F8.10, `arrivi-da-altre-app`): la Share Extension di iOS, sopra l'app di partenza.
 * «Annulla», il titolo e «Salva» in cima; l'anteprima di cio' che arriva (il testo, il link, i file con il loro stato);
 * l'avanzamento della copia; le destinazioni ([destinations], la scelta di partenza da una scorciatoia di condivisione).
 *
 * **Vetro fermo.** Il foglio vive nella finestra trasparente di un'attivita' sopra un'altra app ([FluidShareInWindow]):
 * li' il vetro non puo' piegare cio' che sta sotto, perche' l'altra app non si cattura. Il foglio e' il materiale del
 * modale dell'engine sopra il velo, e dietro la finestra Android sfoca l'altra app (`FLAG_BLUR_BEHIND`) dove puo'. Dove
 * non puo' (prima di Android 12, sfocatura fra finestre spenta) il film e' pieno.
 *
 * [onSave] e' il salvataggio vero dell'app, sospeso: il foglio mostra «Salvo…», poi la spunta, poi chiama [onClose].
 * Un errore lo lascia aperto con «Non si è salvato».
 */
@Composable
fun FluidShareInSheet(
  state: FluidShareInState,
  destinations: List<FluidShareInDestination>,
  onSave: suspend (FluidShareInDestination, FluidShareInContent) -> Unit,
  onClose: () -> Unit,
  title: String,
  initialDestination: String? = null,
  labels: FluidShareInLabels = FluidShareInLabels(),
) {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val haptics = rememberFluidHaptics()
  val scope = rememberCoroutineScope()
  val close by rememberUpdatedState(onClose)
  val density = LocalDensity.current
  val context = LocalContext.current
  val appear = remember { Animatable(if (reduced) 1f else 0f) }
  val drag = remember { Animatable(0f) }
  var sheetHeight by remember { mutableStateOf(0) }
  var chosen by remember(destinations, initialDestination) {
    mutableStateOf(destinations.firstOrNull { it.id == initialDestination } ?: destinations.firstOrNull())
  }
  var failed by remember { mutableStateOf(false) }
  val blur = remember(context) { FluidShareInWindow.blurAvailable(context) }

  LaunchedEffect(Unit) { if (!reduced) appear.animateTo(1f, FluidMotion.PopoverOpenY) }

  fun dismiss() {
    scope.launch {
      if (!reduced) appear.animateTo(0f, spring(1f, FluidShareInExitStiffness))
      close()
    }
  }

  fun save() {
    val target = chosen ?: return
    val content = state.content ?: return
    if (state.phase != FluidShareInPhase.Ready) return
    scope.launch {
      state.phase = FluidShareInPhase.Saving
      failed = false
      try {
        onSave(target, content)
        state.phase = FluidShareInPhase.Saved
        haptics.play(FluidHapticEvent.Success)
        kotlinx.coroutines.delay(FluidShareInSavedMillis)
        dismiss()
      } catch (c: CancellationException) {
        throw c
      } catch (_: Throwable) {
        failed = true
        state.phase = FluidShareInPhase.Ready
        haptics.play(FluidHapticEvent.Error)
      }
    }
  }

  val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
  val backdrop = rememberGlassBackdrop()
  Box(Modifier.fillMaxSize()) {
    // Il velo: lo stesso dei fogli dell'engine (23%, 31% sullo scuro), e la sola cosa che il vetro del foglio campiona.
    Box(
      Modifier
        .fillMaxSize()
        .testTag(FluidShareInTags.Scrim)
        .graphicsLayer { alpha = appear.value.coerceIn(0f, 1f) }
        .glassBackdropSource(backdrop)
        .background(Color.Black.copy(alpha = if (dark) 0.31f else 0.23f))
        .clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = labels.cancel) { dismiss() },
    )
    val shape = ContinuousCornerShape(topStart = FluidRadius.Sheet, topEnd = FluidRadius.Sheet)
    Column(
      Modifier
        .align(Alignment.BottomCenter)
        .widthIn(max = 640.dp)
        .fillMaxWidth()
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(top = 24.dp)
        .onSizeChanged { sheetHeight = it.height }
        // Il foglio continua sotto il bordo dello schermo di [FluidShareInOverhang], come i fogli dell'engine: il bordo
        // in basso del vetro (che campiona oltre se stesso) resta fuori vista, e la barra di navigazione sta dentro il
        // materiale, non sotto una fascia del riempimento (il tablet il 25 novembre: una fascia bianca).
        .graphicsLayer {
          translationY = (1f - appear.value) * sheetHeight + drag.value + FluidShareInOverhang.toPx()
        }
        .testTag(FluidShareInTags.Sheet)
        .semantics { paneTitle = title }
        .background(MaterialTheme.colorScheme.surface.copy(alpha = if (blur) FluidShareInFrostedFill else FluidShareInSolidFill), shape)
        .fluidGlassPanel(backdrop, GlassDefaults.modalTint(), shape, GlassRole.Modal)
        .draggable(
          rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
          Orientation.Vertical,
          onDragStopped = { velocity ->
            val far = drag.value > with(density) { FluidShareInDismissDistance.toPx() }
            val fling = velocity > with(density) { FluidShareInDismissVelocity.toPx() }
            if (far || fling) dismiss() else drag.animateTo(0f, FluidMotion.SheetSettle)
          },
        )
        .windowInsetsPadding(WindowInsets.navigationBars)
        .padding(bottom = FluidShareInOverhang),
    ) {
      // La maniglia: 36 × 5 dp, come i fogli dell'engine.
      Box(
        Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp).size(36.dp, 5.dp)
          .clip(ContinuousCornerShape(3.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)),
      )
      Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        FluidButton(labels.cancel, { dismiss() }, style = FluidButtonStyle.Plain, size = FluidButtonSize.Small,
          modifier = Modifier.testTag(FluidShareInTags.Cancel))
        Text(
          title,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f).semantics { heading() },
        )
        FluidButton(
          labels.save,
          { save() },
          style = FluidButtonStyle.Filled,
          size = FluidButtonSize.Small,
          enabled = state.phase == FluidShareInPhase.Ready && chosen != null,
          loading = state.phase == FluidShareInPhase.Saving,
          modifier = Modifier.testTag(FluidShareInTags.Save),
        )
      }
      Column(
        Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Spacer(Modifier.height(4.dp))
        IncomingPreview(state, labels)
        // Lo stato lo cambia l'app (la copia che avanza, il salvataggio): entra sfocato e lo legge TalkBack.
        FluidGlassContentTransition(
          targetState = statusText(state, labels, failed),
          modifier = Modifier.fillMaxWidth().testTag(FluidShareInTags.Status).semantics { liveRegion = LiveRegionMode.Polite },
          blurReference = 20.dp,
          contentAlignment = Alignment.CenterStart,
        ) { text ->
          if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val progress = state.progress
        if (state.phase == FluidShareInPhase.Copying) {
          FluidProgressBar(progress = { progress?.fraction ?: 0f }, modifier = Modifier.fillMaxWidth())
        }
        if (destinations.isNotEmpty() && state.phase != FluidShareInPhase.Nothing) {
          Text(labels.destination, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
          FluidListGroup {
            destinations.forEachIndexed { index, destination ->
              if (index > 0) FluidListDivider()
              FluidChoiceRow(
                title = destination.title,
                subtitle = destination.subtitle,
                selected = chosen?.id == destination.id,
                onSelect = { chosen = destination },
                modifier = Modifier.testTag(FluidShareInTags.destination(destination.id)),
                leading = destination.icon?.let { icon -> @Composable { FluidIcon(icon, contentDescription = null) } },
              )
            }
          }
        }
        Spacer(Modifier.height(12.dp))
      }
    }
  }
}

/** La riga di stato sotto l'anteprima. */
internal fun statusText(state: FluidShareInState, labels: FluidShareInLabels, failed: Boolean): String {
  if (failed) return labels.failed
  val rejected = state.rejected.takeIf { it.isNotEmpty() }?.joinToString(", ") { copy ->
    "${copy.source.name ?: "?"} (${problemText(copy.problem, labels)})"
  }
  val base = when (state.phase) {
    FluidShareInPhase.Reading -> labels.reading
    FluidShareInPhase.Copying -> labels.copying.format(((state.progress?.fileIndex ?: 0) + 1), (state.progress?.fileCount ?: 1))
    FluidShareInPhase.Saving -> labels.saving
    FluidShareInPhase.Saved -> labels.saved
    FluidShareInPhase.Nothing -> (state.incoming as? FluidIncoming.Unsupported)?.let { "${labels.nothing} (${problemText(it.problem, labels)})" } ?: labels.nothing
    FluidShareInPhase.Ready -> ""
  }
  return listOfNotNull(base.takeIf { it.isNotEmpty() }, rejected?.let { labels.rejected.format(it) }).joinToString("\n")
}

internal fun problemText(problem: FluidShareInProblem?, labels: FluidShareInLabels): String = when (problem) {
  FluidShareInProblem.TooLarge -> labels.tooLarge
  FluidShareInProblem.TooMany -> labels.tooMany
  FluidShareInProblem.TypeNotAccepted -> labels.typeNotAccepted
  FluidShareInProblem.Unreadable -> labels.unreadable
  FluidShareInProblem.UnsafeUri -> labels.unsafe
  FluidShareInProblem.NotAShare -> labels.notAShare
  FluidShareInProblem.Empty -> labels.empty
  null -> ""
}

@Composable
private fun IncomingPreview(state: FluidShareInState, labels: FluidShareInLabels) {
  val locale = Locale.getDefault()
  val shape = ContinuousCornerShape(FluidRadius.Card)
  when (val now = state.incoming) {
    is FluidIncoming.Text -> Column(
      Modifier.testTag(FluidShareInTags.Preview).fillMaxWidth().clip(shape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(14.dp),
    ) {
      if (now.selection) Text(labels.textSelection, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
      now.subject?.let { Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
      Text(now.text, style = MaterialTheme.typography.bodyMedium, maxLines = 8, overflow = TextOverflow.Ellipsis)
    }
    is FluidIncoming.Link -> Row(
      Modifier.testTag(FluidShareInTags.Preview).fillMaxWidth().clip(shape)
        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidIcon(FluidIcons.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(now.title ?: hostOf(now.url), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(now.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
    is FluidIncoming.Files -> Row(
      Modifier.testTag(FluidShareInTags.Preview).fillMaxWidth().horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      now.files.forEach { file ->
        val copy = state.copies.firstOrNull { it.source.uri == file.uri }
        Box(Modifier.width(220.dp).semantics(mergeDescendants = true) { contentDescription = file.name ?: "" }) {
          FluidFileTile(
            name = file.name ?: "?",
            onClick = {},
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes,
            locale = locale,
          )
          if (copy?.ok == false) {
            Text(
              problemText(copy.problem, labels),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
            )
          }
        }
      }
    }
    else -> Unit
  }
}

private fun hostOf(url: String): String = runCatching { android.net.Uri.parse(url).host }.getOrNull()?.removePrefix("www.") ?: url

/** La pausa sulla spunta di «Salvato» prima di chiudere. A occhio, 24 novembre 2026. */
internal const val FluidShareInSavedMillis: Long = 500L

/** La molla dell'uscita: smorzamento critico, rigidezza 400 (a occhio, 24 novembre 2026). */
internal const val FluidShareInExitStiffness: Float = 400f

/** Il foglio trascinato giu' oltre 110 dp, o lanciato oltre 900 dp/s, si chiude: gli stessi numeri di FluidGlassSheet. */
internal val FluidShareInDismissDistance = 110.dp

/**
 * Quanto il foglio degli arrivi continua sotto il bordo dello schermo: 48 dp. Abbastanza perche' il bordo del vetro, e il
 * suo riflesso, stiano fuori vista anche con un dito che lo tira un poco in su. A occhio, 25 novembre 2026.
 */
internal val FluidShareInOverhang = 48.dp
internal val FluidShareInDismissVelocity = 900.dp

/** Il film sotto il vetro: piu' leggero dove Android sfoca l'app dietro, pieno dove non puo'. A occhio, 24 novembre 2026. */
internal const val FluidShareInFrostedFill: Float = 0.62f
internal const val FluidShareInSolidFill: Float = 0.97f

/**
 * La finestra dell'attivita' che riceve (4.0, F8.10): trasparente sopra l'app di partenza, da bordo a bordo, con la
 * sfocatura di sistema dietro (Android 12 e oltre, `FLAG_BLUR_BEHIND`) quando il dispositivo la permette. Da chiamare
 * in `onCreate`, prima di `setContent`, in un'attivita' col tema `Theme.FluidShareIn`.
 */
object FluidShareInWindow {
  /** Il raggio della sfocatura dietro, a occhio (24 novembre 2026): quanto basta perche' il testo dietro non si legga. */
  const val BlurDp: Float = 24f

  fun install(activity: Activity) {
    val window = activity.window
    WindowCompat.setDecorFitsSystemWindows(window, false)
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
    if (Build.VERSION.SDK_INT >= 31 && blurAvailable(activity)) {
      window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
      window.attributes = window.attributes.also {
        it.blurBehindRadius = (BlurDp * activity.resources.displayMetrics.density).toInt()
      }
    }
  }

  /** Vero se Android sfoca dietro la finestra (Android 12 e oltre, sfocatura fra finestre accesa). */
  fun blurAvailable(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 31) return false
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
    return runCatching { wm.isCrossWindowBlurEnabled }.getOrDefault(false)
  }
}

/**
 * Le scorciatoie di condivisione (4.0, F8.10): le ultime destinazioni scelte compaiono in alto nel foglio di Android,
 * come i suggerimenti di iOS. L'ordine lo decide Android, non l'app. L'app dichiara in `res/xml/shortcuts.xml` un
 * `<share-target>` con la sua attivita' e la stessa [category], e mette il meta-data `android.app.shortcuts` su
 * un'attivita' di lancio.
 */
object FluidShareInShortcuts {
  /** Pubblica (o rinfresca) [destination] come destinazione di condivisione verso [activity]. */
  fun publish(context: Context, destination: FluidShareInDestination, activity: Class<out Activity>, category: String) {
    val icon = IconCompat.createWithResource(context, context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.ic_menu_share)
    val shortcut = ShortcutInfoCompat.Builder(context, shortcutId(destination.id))
      .setShortLabel(destination.title)
      .setLongLived(true)
      .setIcon(icon)
      .setCategories(setOf(category))
      .setIntent(Intent(Intent.ACTION_DEFAULT).setClass(context, activity))
      .build()
    runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
  }

  /** La destinazione chiesta dalla scorciatoia che ha aperto l'attivita', se e' una delle nostre. */
  fun destinationOf(intent: Intent?): String? =
    intent?.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)?.takeIf { it.startsWith(Prefix) }?.removePrefix(Prefix)

  internal const val Prefix = "fluid-share-in:"
  internal fun shortcutId(id: String): String = Prefix + id
}
