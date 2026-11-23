package dev.antigravity.fluidengine.aiui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons

// L'allegato con la sua anteprima nel vassoio del composer (F6B.16). Alessio, 23 novembre 2026, sul vassoio
// dell'assistente con «Pianta del secondo piano»: «voglio anche le anteprime delle foto caricate». Prima l'allegato era
// una pillola alta 40 dp con l'icona del tipo e il nome ([FluidComposerPill]): un'anteprima ci starebbe in un francobollo.
// Qui è un riquadro da 64 dp, come in Messaggi: la foto ritagliata, o la miniatura del file accanto al suo nome.

/** Come si mostra un allegato nel vassoio del composer (F6B.16). */
enum class FluidComposerAttachmentStyle {
  /** Una foto o un video: l'anteprima riempie il riquadro, ritagliata, senza scritta (il nome lo legge TalkBack). */
  Photo,

  /** Un documento o un file qualunque: la miniatura (la prima pagina, il foglio, l'icona) accanto al nome. */
  File,
}

/**
 * Le misure dell'allegato con anteprima (F6B.16). **A occhio, scelta di F6B.16 (23 novembre 2026)**: 64 dp invece dei 40
 * della pillola (F6B.8b), perché a 40 una foto ritagliata non si riconosce; 64 è il riquadro delle anteprime nel campo
 * di Messaggi, e il vassoio cresce sopra il testo quanto serve.
 */
object FluidComposerAttachmentDefaults {
  /** L'altezza del riquadro, e il lato di una foto. */
  val Height: Dp = 64.dp

  /** L'anteprima di un file: un foglio verticale nel riquadro. */
  val FilePreviewWidth: Dp = 44.dp
  val FilePreviewHeight: Dp = 56.dp

  /** Quanto si allarga al più il nome di un file, che va a capo su due righe. */
  val FileLabelMaxWidth: Dp = 132.dp

  /** Il cerchio della X sopra una foto, e la X accanto a un file. */
  val RemoveDisc: Dp = 22.dp
  val RemoveGlyph: Dp = 14.dp

  /** Il bersaglio della X quando il riquadro apre l'allegato ([FluidComposerAttachment] con `onOpen`). */
  val RemoveTarget: Dp = 48.dp

  /** Il raggio del riquadro: quello dei controlli. */
  val Radius: Dp = FluidRadius.Control

  /** Il velo del cerchio della X sopra una foto: scuro su ogni foto, come in iOS, con la X bianca. */
  val RemoveVeil: Color = Color.Black.copy(alpha = 0.55f)
}

/**
 * Un allegato del vassoio del composer **con la sua anteprima** (F6B.16): per una foto la foto ritagliata nel riquadro,
 * per gli altri file la miniatura accanto al nome. L'anteprima la dà l'app in [preview]: il composer non legge file. Con
 * `engine-viewer` è la miniatura vera di `FluidFileThumbnails` (F6C.17), con la sua cache:
 *
 * ```
 * val px = with(LocalDensity.current) { FluidComposerAttachmentDefaults.Height.roundToPx() }
 * FluidComposerAttachment("Pianta", onRemove = { … }, style = FluidComposerAttachmentStyle.Photo) {
 *   rememberFluidFileThumbnail(file, px)?.let { FluidFileThumbnailImage(it) }
 * }
 * ```
 *
 * Il tocco sul riquadro toglie l'allegato, come la pillola di prima ([FluidComposerPill]): il bersaglio è il riquadro,
 * 64 dp. Con [onOpen] il riquadro apre l'allegato e la X ha un suo bersaglio da 48 dp nell'angolo. Finché l'anteprima
 * non c'è il riquadro mostra il suo fondo.
 *
 * **Quando compare si fa vedere intero** (F6B.16, dopo le riprese sul tablet del 23 novembre 2026): chiede al vassoio
 * che lo tiene di scorrere finché il riquadro, con la sua X, sta tutto dentro. Il vassoio dell'assistente scorre di lato
 * sotto il chip del modello: sul telefono il PDF appena aggiunto usciva a destra con la X, finché non si scorreva a mano.
 * Così l'ultimo allegato aggiunto è sempre quello in vista; un vassoio che non scorre non si muove.
 *
 * @param label il nome: scritto accanto a un file, letto da TalkBack per una foto.
 * @param removeDescription ciò che TalkBack dice della X.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FluidComposerAttachment(
  label: String,
  onRemove: () -> Unit,
  modifier: Modifier = Modifier,
  style: FluidComposerAttachmentStyle = FluidComposerAttachmentStyle.File,
  removeDescription: String = "Togli $label",
  onOpen: (() -> Unit)? = null,
  preview: @Composable BoxScope.() -> Unit,
) {
  val shape = ContinuousCornerShape(FluidComposerAttachmentDefaults.Radius)
  val well = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
  val ink = MaterialTheme.colorScheme.onSurface
  val tap = onOpen ?: onRemove
  // L'allegato appena aggiunto si porta in vista nel vassoio, X compresa: al primo fotogramma, quando il riquadro è
  // posato e il vassoio sa quanto è lungo.
  val reveal = remember { BringIntoViewRequester() }
  LaunchedEffect(reveal) {
    withFrameNanos { }
    reveal.bringIntoView()
  }
  val tile = modifier
    .bringIntoViewRequester(reveal)
    .height(FluidComposerAttachmentDefaults.Height)
    .clip(shape)
    .background(well)
    .fluidPressable(onClick = tap, role = Role.Button, haptic = null)
    .semantics {
      contentDescription = if (onOpen == null) "$label, $removeDescription" else label
    }
  when (style) {
    FluidComposerAttachmentStyle.Photo -> Box(tile.size(FluidComposerAttachmentDefaults.Height)) {
      Box(Modifier.matchParentSize(), content = preview)
      ComposerAttachmentRemove(onRemove, removeDescription, onOpen != null, Modifier.align(Alignment.TopEnd)) {
        Box(
          Modifier.padding(5.dp).size(FluidComposerAttachmentDefaults.RemoveDisc)
            .background(FluidComposerAttachmentDefaults.RemoveVeil, FluidCapsuleShape),
          contentAlignment = Alignment.Center,
        ) { FluidIcon(FluidIcons.Close, null, tint = Color.White, size = FluidComposerAttachmentDefaults.RemoveGlyph) }
      }
    }
    FluidComposerAttachmentStyle.File -> Row(tile.padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(
        Modifier.size(FluidComposerAttachmentDefaults.FilePreviewWidth, FluidComposerAttachmentDefaults.FilePreviewHeight)
          .clip(ContinuousCornerShape(6.dp)),
        content = preview,
      )
      Spacer(Modifier.width(8.dp))
      Text(
        label, Modifier.widthIn(max = FluidComposerAttachmentDefaults.FileLabelMaxWidth),
        style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
      )
      ComposerAttachmentRemove(onRemove, removeDescription, onOpen != null, Modifier.fillMaxHeight()) {
        Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
          FluidIcon(FluidIcons.Close, null, tint = ink.copy(alpha = 0.7f), size = FluidComposerAttachmentDefaults.RemoveGlyph + 2.dp)
        }
      }
    }
  }
}

/**
 * La X dell'allegato: un segno solo se il tocco sul riquadro già toglie l'allegato; un tasto con il suo bersaglio da 48 dp
 * se il riquadro apre ([separate]).
 */
@Composable
private fun ComposerAttachmentRemove(
  onRemove: () -> Unit,
  description: String,
  separate: Boolean,
  modifier: Modifier,
  glyph: @Composable () -> Unit,
) {
  if (!separate) {
    Box(modifier.clearAndSetSemantics { }, contentAlignment = Alignment.Center) { glyph() }
    return
  }
  Box(
    modifier
      .size(FluidComposerAttachmentDefaults.RemoveTarget)
      .fluidPressable(onClick = onRemove, role = Role.Button, haptic = null)
      .clearAndSetSemantics {
        contentDescription = description
        role = Role.Button
        onClick { onRemove(); true }
      },
    contentAlignment = Alignment.TopEnd,
  ) { glyph() }
}
